package dev.fuzz.meta.listenbrainz

import dev.fuzz.core.domain.plugin.AlbumRef
import dev.fuzz.core.domain.plugin.ArtistRef
import dev.fuzz.core.domain.plugin.ExternalId
import dev.fuzz.core.domain.plugin.MetadataProvider
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.Providers
import dev.fuzz.core.domain.plugin.Similar
import dev.fuzz.core.domain.plugin.Tag
import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.core.network.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import java.io.IOException

/**
 * ListenBrainz Labs: похожие артисты по открытым данным прослушиваний. Работает по MBID артиста
 * (из [ArtistRef.externalIds], его даёт MusicBrainz); без MBID вернёт NOT_FOUND.
 * [algorithm] — имя алгоритма Labs API, при смене на сервере его можно подменить без пересборки модуля.
 * Клиент должен иметь ContentNegotiation с JSON (ignoreUnknownKeys = true).
 */
class ListenBrainzProvider(
    private val client: HttpClient,
    private val algorithm: String = DEFAULT_ALGORITHM,
    private val baseUrl: String = "https://labs.api.listenbrainz.org",
) : MetadataProvider {
    override val id = "listenbrainz"
    override val displayName = "ListenBrainz"

    override suspend fun similarArtists(artist: ArtistRef, limit: Int): PluginResult<List<Similar<ArtistRef>>> {
        val mbid =
            artist.externalIds.firstOrNull { it.provider == Providers.MUSICBRAINZ }?.id
                ?: return PluginResult.Failure(PluginResult.Kind.NOT_FOUND, "artist MBID required")
        return safeCall {
            val response =
                client.get("$baseUrl/similar-artists/json") {
                    parameter("artist_mbids", mbid)
                    parameter("algorithm", algorithm)
                }
            if (response.status != HttpStatusCode.OK) throw IOException("ListenBrainz HTTP ${response.status.value}")
            val items = response.body<List<SimilarArtistDto>>()
            val top = items.maxOfOrNull { it.score }?.takeIf { it > 0 } ?: 1.0
            items.asSequence()
                .filter { it.artistMbid != mbid && it.name.isNotBlank() }
                .take(limit)
                .map {
                    Similar(
                        ArtistRef(it.name, listOf(ExternalId(Providers.MUSICBRAINZ, it.artistMbid))),
                        (it.score / top).toFloat(),
                    )
                }
                .toList()
        }
    }

    override suspend fun similarTracks(track: TrackRef, limit: Int) = PluginResult.Ok(emptyList<Similar<TrackRef>>())

    override suspend fun tags(track: TrackRef) = PluginResult.Ok(emptyList<Tag>())

    override suspend fun discography(artist: ArtistRef) = PluginResult.Ok(emptyList<AlbumRef>())

    override suspend fun coverUrl(album: AlbumRef): PluginResult<String> =
        PluginResult.Failure(PluginResult.Kind.NOT_FOUND, "covers come from Cover Art Archive")

    companion object {
        const val DEFAULT_ALGORITHM =
            "session_based_days_9000_session_300_contribution_5_threshold_15_limit_50_skip_30"
    }
}
