package dev.fuzz.meta.lastfm

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
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import java.io.IOException

/**
 * Last.fm: похожие артисты и треки, теги. [apiKey] — бесплатный ключ, в репозиторий не коммитится.
 * Клиент должен иметь ContentNegotiation с JSON (ignoreUnknownKeys = true). Last.fm отвечает
 * кодом 400, когда артист или трек неизвестен, это превращается в NOT_FOUND.
 */
class LastFmProvider(
    private val client: HttpClient,
    private val apiKey: String,
    private val limiter: RateLimiter = RateLimiter(MIN_INTERVAL_MS),
    private val baseUrl: String = "https://ws.audioscrobbler.com/2.0/",
) : MetadataProvider {
    override val id = "lastfm"
    override val displayName = "Last.fm"

    override suspend fun similarArtists(artist: ArtistRef, limit: Int): PluginResult<List<Similar<ArtistRef>>> =
        call<SimilarArtistsResponse>("artist.getsimilar", limit) { parameter("artist", artist.name) }.map { resp ->
            resp.similarartists?.artist.orEmpty().map {
                Similar(ArtistRef(it.name, mbidIds(it.mbid)), it.match.score())
            }
        }

    override suspend fun similarTracks(track: TrackRef, limit: Int): PluginResult<List<Similar<TrackRef>>> =
        call<SimilarTracksResponse>("track.getsimilar", limit) {
            parameter("artist", track.artist)
            parameter("track", track.title)
        }.map { resp ->
            resp.similartracks?.track.orEmpty().map {
                Similar(
                    TrackRef(
                        title = it.name,
                        artist = it.artist.name,
                        durationMs = it.duration?.content?.toLongOrNull()?.takeIf { s -> s > 0 }?.times(MS_IN_SECOND),
                        externalIds = mbidIds(it.mbid),
                    ),
                    it.match.score(),
                )
            }
        }

    override suspend fun tags(track: TrackRef): PluginResult<List<Tag>> =
        call<TopTagsResponse>("track.gettoptags", null) {
            parameter("artist", track.artist)
            parameter("track", track.title)
        }.map { resp -> resp.toptags?.tag.orEmpty().map { Tag(it.name, it.count) } }

    override suspend fun discography(artist: ArtistRef) = PluginResult.Ok(emptyList<AlbumRef>())

    override suspend fun coverUrl(album: AlbumRef): PluginResult<String> =
        PluginResult.Failure(PluginResult.Kind.NOT_FOUND, "covers come from Cover Art Archive")

    private suspend inline fun <reified T> call(
        method: String,
        limit: Int?,
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
    ): PluginResult<T> {
        var notFound = false
        val result =
            safeCall {
                limiter.run {
                    val response =
                        client.get(baseUrl) {
                            parameter("method", method)
                            parameter("api_key", apiKey)
                            parameter("format", "json")
                            parameter("autocorrect", 1)
                            limit?.let { parameter("limit", it) }
                            block()
                        }
                    when (response.status) {
                        HttpStatusCode.OK -> response.body<T>()
                        HttpStatusCode.BadRequest -> null.also { notFound = true }
                        else -> throw IOException("Last.fm HTTP ${response.status.value}")
                    }
                }
            }
        return when {
            result is PluginResult.Failure -> result
            notFound -> PluginResult.Failure(PluginResult.Kind.NOT_FOUND)
            else -> PluginResult.Ok((result as PluginResult.Ok).value as T)
        }
    }

    private fun mbidIds(mbid: String?) =
        mbid?.takeIf { it.isNotBlank() }?.let { listOf(ExternalId(Providers.MUSICBRAINZ, it)) }.orEmpty()

    private fun kotlinx.serialization.json.JsonPrimitive?.score() = this?.content?.toFloatOrNull() ?: 0f

    private inline fun <T, R> PluginResult<T>.map(transform: (T) -> R): PluginResult<R> =
        when (this) {
            is PluginResult.Ok -> PluginResult.Ok(transform(value))
            is PluginResult.Failure -> this
        }

    private companion object {
        const val MIN_INTERVAL_MS = 250L
        const val MS_IN_SECOND = 1_000L
    }
}
