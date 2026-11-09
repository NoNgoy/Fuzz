package dev.fuzz.source.deezerpreview

import dev.fuzz.core.domain.artist.AlbumProfile
import dev.fuzz.core.domain.artist.ArtistDetails
import dev.fuzz.core.domain.artist.ArtistDirectory
import dev.fuzz.core.domain.artist.ArtistProfile
import dev.fuzz.core.domain.plugin.TrackRef
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.IOException

/**
 * Артисты и альбомы из открытого API Deezer: фото, популярные треки, дискография, похожие.
 * Ключ не нужен. Звук отсюда только 30-секундный, полные версии ищутся в подключениях.
 */
class DeezerArtistDirectory(
    private val client: HttpClient,
    private val baseUrl: String = "https://api.deezer.com",
) : ArtistDirectory {
    override suspend fun searchArtists(
        query: String,
        limit: Int,
    ): List<ArtistProfile> =
        orEmpty {
            get<DeezerArtistList>("$baseUrl/search/artist") {
                parameter("q", query)
                parameter("limit", limit)
            }.data.filter { it.id != 0L }.map { it.toProfile() }
        }

    override suspend fun details(artist: ArtistProfile): ArtistDetails? {
        val id = artist.id ?: findId(artist.name) ?: return null
        return orNull {
            coroutineScope {
                val profile = async { get<DeezerArtistDto>("$baseUrl/artist/$id") {} }
                val top = async { get<DeezerSearchResponse>("$baseUrl/artist/$id/top") { parameter("limit", TOP) } }
                val albums = async { get<DeezerAlbumList>("$baseUrl/artist/$id/albums") { parameter("limit", ALBUMS) } }
                val related =
                    async { get<DeezerArtistList>("$baseUrl/artist/$id/related") { parameter("limit", RELATED) } }
                val found = profile.await().takeIf { it.id != 0L }?.toProfile() ?: return@coroutineScope null
                ArtistDetails(
                    artist = found,
                    top = top.await().data.filter { it.id != 0L }.map { it.toRef() },
                    albums =
                        albums
                            .await()
                            .data
                            .filter { it.id != 0L }
                            .map { it.toProfile(found.name) }
                            .sortedByDescending { it.year ?: 0 },
                    related = related.await().data.filter { it.id != 0L }.map { it.toProfile() },
                )
            }
        }
    }

    override suspend fun albumTracks(album: AlbumProfile): List<TrackRef> =
        orEmpty {
            get<DeezerSearchResponse>("$baseUrl/album/${album.id}/tracks") { parameter("limit", ALBUM_TRACKS) }
                .data
                .filter { it.id != 0L }
                // В ответе /album/{id}/tracks нет обложки и названия альбома: берём их из карточки.
                .map { it.toRef(cover = album.coverUrl).copy(album = album.title) }
        }

    /** Точное совпадение имени важнее популярности: «Кино» не должно стать «Кино и Немцы». */
    private suspend fun findId(name: String): String? {
        val found = searchArtists(name, FIND_LIMIT)
        return (found.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: found.firstOrNull())?.id
    }

    private suspend inline fun <reified T> get(
        url: String,
        block: HttpRequestBuilder.() -> Unit,
    ): T =
        client.get(url) { block() }.let { response ->
            if (response.status != HttpStatusCode.OK) throw IOException("Deezer HTTP ${response.status.value}")
            response.body<T>()
        }

    private suspend inline fun <T> orEmpty(block: () -> List<T>): List<T> = orNull(block).orEmpty()

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend inline fun <T> orNull(block: () -> T?): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Нет сети, Deezer недоступен из региона или ответ не в том формате: карточек просто не будет.
            null
        }

    private fun DeezerArtistDto.toProfile() =
        ArtistProfile(name = name, pictureUrl = pictureXl ?: pictureMedium, fans = fans, id = id.toString())

    private fun DeezerAlbumDto.toProfile(artist: String) =
        AlbumProfile(
            id = id.toString(),
            title = title,
            artist = artist,
            coverUrl = coverXl ?: coverMedium,
            year = releaseDate?.take(YEAR_CHARS)?.toIntOrNull(),
        )

    private companion object {
        const val TOP = 10
        const val ALBUMS = 50
        const val RELATED = 12
        const val ALBUM_TRACKS = 100
        const val FIND_LIMIT = 5
        const val YEAR_CHARS = 4
    }
}
