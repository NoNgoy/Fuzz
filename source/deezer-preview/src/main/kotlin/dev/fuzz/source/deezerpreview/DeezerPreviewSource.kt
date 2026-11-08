package dev.fuzz.source.deezerpreview

import dev.fuzz.core.domain.plugin.ExternalId
import dev.fuzz.core.domain.plugin.MusicSource
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.Providers
import dev.fuzz.core.domain.plugin.SearchResult
import dev.fuzz.core.domain.plugin.SourceCapability
import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.core.network.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import java.io.IOException

/**
 * Deezer: 30-секундные превью треков, которых нет в коллекции. Ключ не нужен.
 * Если API недоступен (например, из РФ без VPN), вызовы вернут Failure, а превью просто скрываются.
 * Клиент должен иметь ContentNegotiation с JSON (ignoreUnknownKeys = true).
 */
class DeezerPreviewSource(
    private val client: HttpClient,
    private val baseUrl: String = "https://api.deezer.com",
) : MusicSource {
    override val id = "deezer-preview"
    override val displayName = "Deezer (превью)"
    override val capabilities = setOf(SourceCapability.PREVIEW_ONLY)

    override suspend fun search(query: String, limit: Int): PluginResult<SearchResult> =
        safeCall {
            val tracks =
                fetch("$baseUrl/search") {
                    parameter("q", query)
                    parameter("limit", limit)
                }
            SearchResult(tracks = tracks.filter { !it.preview.isNullOrBlank() }.map { it.toRef() })
        }

    override suspend fun playbackUri(track: TrackRef): PluginResult<String> {
        val query = "artist:\"${track.artist.clean()}\" track:\"${track.title.clean()}\""
        val result =
            safeCall {
                fetch("$baseUrl/search") {
                    parameter("q", query)
                    parameter("limit", 1)
                }.firstOrNull { !it.preview.isNullOrBlank() }
            }
        return when (result) {
            is PluginResult.Failure -> {
                result
            }

            is PluginResult.Ok -> {
                result.value?.preview?.let { PluginResult.Ok(it) }
                    ?: PluginResult.Failure(PluginResult.Kind.NOT_FOUND)
            }
        }
    }

    private suspend fun fetch(url: String, block: io.ktor.client.request.HttpRequestBuilder.() -> Unit) =
        client.get(url) { block() }.let { response ->
            if (response.status != HttpStatusCode.OK) throw IOException("Deezer HTTP ${response.status.value}")
            response.body<DeezerSearchResponse>().data
        }

    private fun String.clean() = replace("\"", "")

    private fun DeezerTrackDto.toRef() =
        TrackRef(
            title = title,
            artist = artist?.name.orEmpty(),
            album = album?.title,
            durationMs = duration?.times(MS_IN_SECOND),
            externalIds = listOf(ExternalId(Providers.DEEZER, id.toString())),
        )

    private companion object {
        const val MS_IN_SECOND = 1_000L
    }
}
