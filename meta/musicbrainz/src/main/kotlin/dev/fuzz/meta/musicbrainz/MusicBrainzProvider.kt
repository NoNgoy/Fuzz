package dev.fuzz.meta.musicbrainz

import dev.fuzz.core.domain.plugin.AlbumRef
import dev.fuzz.core.domain.plugin.ArtistRef
import dev.fuzz.core.domain.plugin.ExternalId
import dev.fuzz.core.domain.plugin.MetadataProvider
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.Providers
import dev.fuzz.core.domain.plugin.Similar
import dev.fuzz.core.domain.plugin.Tag
import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.core.network.RateLimiter
import dev.fuzz.core.network.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.io.IOException

/**
 * MusicBrainz + Cover Art Archive: дискографии и обложки. Лимит API: 1 запрос в секунду,
 * поэтому все вызовы идут через общую очередь [limiter]. [userAgent] обязателен по правилам сервиса.
 * Клиент должен иметь ContentNegotiation с JSON (ignoreUnknownKeys = true).
 */
class MusicBrainzProvider(
    private val client: HttpClient,
    private val userAgent: String,
    private val limiter: RateLimiter = RateLimiter(MIN_INTERVAL_MS),
    private val baseUrl: String = "https://musicbrainz.org/ws/2",
    private val coverBaseUrl: String = "https://coverartarchive.org",
) : MetadataProvider {
    override val id = "musicbrainz"
    override val displayName = "MusicBrainz"

    override suspend fun similarArtists(artist: ArtistRef, limit: Int) =
        PluginResult.Ok(emptyList<Similar<ArtistRef>>())

    override suspend fun similarTracks(track: TrackRef, limit: Int) =
        PluginResult.Ok(emptyList<Similar<TrackRef>>())

    override suspend fun tags(track: TrackRef) = PluginResult.Ok(emptyList<Tag>())

    override suspend fun discography(artist: ArtistRef): PluginResult<List<AlbumRef>> {
        val mbid =
            when (val found = artistMbid(artist)) {
                is PluginResult.Ok -> found.value
                is PluginResult.Failure -> return found
            }
        return safeCall {
            val response =
                request("$baseUrl/release-group") {
                    parameter("artist", mbid)
                    parameter("type", "album")
                    parameter("limit", MAX_ALBUMS)
                    parameter("fmt", "json")
                }
            response.body<ReleaseGroupResponse>().releaseGroups.map {
                AlbumRef(
                    title = it.title,
                    artist = artist.name,
                    year = it.firstReleaseDate?.take(YEAR_LENGTH)?.toIntOrNull(),
                    externalIds = listOf(ExternalId(Providers.MUSICBRAINZ, it.id)),
                )
            }
        }
    }

    /** Обложка по MBID группы релизов из [AlbumRef.externalIds]; без MBID вернёт NOT_FOUND. */
    override suspend fun coverUrl(album: AlbumRef): PluginResult<String> {
        val mbid =
            album.externalIds.firstOrNull { it.provider == Providers.MUSICBRAINZ }?.id
                ?: return PluginResult.Failure(PluginResult.Kind.NOT_FOUND, "no MusicBrainz id")
        return PluginResult.Ok("$coverBaseUrl/release-group/$mbid/front-$COVER_SIZE")
    }

    private suspend fun artistMbid(artist: ArtistRef): PluginResult<String> {
        artist.externalIds.firstOrNull { it.provider == Providers.MUSICBRAINZ }?.let {
            return PluginResult.Ok(it.id)
        }
        val result =
            safeCall {
                request("$baseUrl/artist") {
                    parameter("query", "artist:\"${artist.name.replace("\"", "")}\"")
                    parameter("limit", 1)
                    parameter("fmt", "json")
                }.body<ArtistSearchResponse>().artists.firstOrNull()?.id
            }
        return when (result) {
            is PluginResult.Ok -> {
                result.value?.let { PluginResult.Ok(it) } ?: PluginResult.Failure(PluginResult.Kind.NOT_FOUND)
            }

            is PluginResult.Failure -> {
                result
            }
        }
    }

    private suspend fun request(
        url: String,
        block: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
    ): HttpResponse =
        limiter.run {
            val response =
                client.get(url) {
                    header(HttpHeaders.UserAgent, userAgent)
                    block()
                }
            when (response.status) {
                HttpStatusCode.OK -> response
                else -> throw IOException("MusicBrainz HTTP ${response.status.value}")
            }
        }

    private companion object {
        const val MIN_INTERVAL_MS = 1_100L
        const val MAX_ALBUMS = 100
        const val YEAR_LENGTH = 4
        const val COVER_SIZE = 500
    }
}
