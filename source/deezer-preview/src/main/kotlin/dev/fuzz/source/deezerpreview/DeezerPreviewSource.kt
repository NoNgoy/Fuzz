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
import java.util.concurrent.ConcurrentHashMap

/**
 * Deezer: 30-секундные превью треков, которых нет в коллекции. Ключ не нужен.
 * Если API недоступен (например, из РФ без VPN), вызовы вернут Failure, а превью просто скрываются.
 * Клиент должен иметь ContentNegotiation с JSON (ignoreUnknownKeys = true).
 */
class DeezerPreviewSource(
    private val client: HttpClient,
    private val baseUrl: String = "https://api.deezer.com",
    private val now: () -> Long = System::currentTimeMillis,
) : MusicSource {
    /**
     * Ссылки на превью из последних поисков по id трека Deezer. Ссылки подписаны и со временем
     * истекают, поэтому старые запрашиваются заново по id.
     */
    private val previews = ConcurrentHashMap<String, Pair<String, Long>>()

    override val id = "deezer-preview"
    override val displayName = "Deezer (превью)"
    override val capabilities = setOf(SourceCapability.PREVIEW_ONLY)

    override suspend fun search(query: String, limit: Int): PluginResult<SearchResult> = searchPage(query, limit, 0)

    override suspend fun searchPage(query: String, limit: Int, offset: Int): PluginResult<SearchResult> =
        safeCall {
            val tracks =
                fetch("$baseUrl/search") {
                    parameter("q", query)
                    parameter("limit", limit)
                    parameter("index", offset)
                }
            val withPreview = tracks.filter { !it.preview.isNullOrBlank() }
            remember(withPreview)
            SearchResult(tracks = withPreview.map { it.toRef() })
        }

    /**
     * Сначала ссылка из поиска, затем трек по id, и только без id — поиск по названию.
     * Раньше всегда искали по названию: на телефоне это давало «нет ссылки» для найденных треков.
     */
    override suspend fun playbackUri(track: TrackRef): PluginResult<String> {
        val id =
            track.externalIds.firstOrNull { it.provider == Providers.DEEZER }?.id
                ?: return playbackUriBySearch(track)
        previews[id]?.takeIf { (_, at) -> now() - at < PREVIEW_TTL_MS }?.let { return PluginResult.Ok(it.first) }
        val byId = safeCall { fetchTrack(id) }
        val preview = (byId as? PluginResult.Ok)?.value?.preview?.takeIf { it.isNotBlank() }
        return if (preview != null) {
            previews[id] = preview to now()
            PluginResult.Ok(preview)
        } else {
            playbackUriBySearch(track)
        }
    }

    private suspend fun playbackUriBySearch(track: TrackRef): PluginResult<String> {
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

    private suspend fun fetchTrack(id: String): DeezerTrackDto =
        client.get("$baseUrl/track/$id").let { response ->
            if (response.status != HttpStatusCode.OK) throw IOException("Deezer HTTP ${response.status.value}")
            response.body<DeezerTrackDto>()
        }

    private fun remember(tracks: List<DeezerTrackDto>) {
        if (previews.size > MAX_PREVIEWS) previews.clear()
        val at = now()
        tracks.forEach { dto -> dto.preview?.let { previews[dto.id.toString()] = it to at } }
    }

    private fun String.clean() = replace("\"", "")

    private companion object {
        /** Подпись ссылки Deezer живёт дольше; берём с запасом. */
        const val PREVIEW_TTL_MS = 10 * 60 * 1_000L
        const val MAX_PREVIEWS = 2_000
    }
}

private const val MS_IN_SECOND = 1_000L

internal fun DeezerTrackDto.toRef(cover: String? = null) =
    TrackRef(
        title = title,
        artist = artist?.name.orEmpty(),
        album = album?.title,
        durationMs = duration?.times(MS_IN_SECOND),
        externalIds = listOf(ExternalId(Providers.DEEZER, id.toString())),
        artworkUrl = album?.coverMedium ?: cover,
    )
