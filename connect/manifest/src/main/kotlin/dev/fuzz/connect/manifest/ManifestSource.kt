package dev.fuzz.connect.manifest

import dev.fuzz.core.domain.plugin.ExternalId
import dev.fuzz.core.domain.plugin.MusicSource
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.SearchResult
import dev.fuzz.core.domain.plugin.SourceCapability
import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.core.network.RateLimiter
import io.ktor.client.HttpClient
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.JsonElement

/**
 * Подключение, собранное из [Manifest]: поиск треков и потоковая ссылка. Запросы идут только на домен
 * `baseUrl`, ответ режется по размеру, число запросов ограничено [limiter]. [secret] (ключ, токен или
 * `логин:пароль`) приходит из защищённого хранилища, в манифесте его нет. Тексты и похожие из того же
 * манифеста даёт [ManifestConnection].
 */
class ManifestSource internal constructor(
    private val manifest: Manifest,
    private val http: ManifestHttp,
) : MusicSource {
    constructor(
        client: HttpClient,
        manifest: Manifest,
        secret: String? = null,
        limiter: RateLimiter = RateLimiter(ManifestConnection.MIN_INTERVAL_MS),
    ) : this(manifest, ManifestHttp(client, manifest, secret, limiter))

    override val id = manifest.id
    override val displayName = manifest.name
    override val capabilities = setOf(SourceCapability.STREAM)

    override suspend fun search(query: String, limit: Int): PluginResult<SearchResult> {
        val spec = manifest.search
        val vars = mapOf("query" to query, "limit" to limit.toString())
        return when (val result = http.getJson(spec.path, spec.query, vars)) {
            is PluginResult.Failure -> result
            is PluginResult.Ok -> PluginResult.Ok(SearchResult(tracks = extractTracks(result.value).take(limit)))
        }
    }

    override suspend fun playbackUri(track: TrackRef): PluginResult<String> {
        val externalId =
            track.externalIds.firstOrNull { it.provider == manifest.id }?.id
                ?: return PluginResult.Failure(PluginResult.Kind.NOT_FOUND, "no ${manifest.id} id")
        return PluginResult.Ok(manifest.stream.url.replace("{id}", externalId.encodeURLParameter()))
    }

    private fun extractTracks(root: JsonElement): List<TrackRef> {
        val map = manifest.search.map
        return JsonPathLite.select(root, manifest.search.results).mapNotNull { item ->
            val trackId = JsonPathLite.text(item, map.id)
            val title = JsonPathLite.text(item, map.title)
            if (trackId == null || title == null) return@mapNotNull null
            TrackRef(
                title = title,
                artist = JsonPathLite.text(item, map.artist).orEmpty(),
                album = map.album?.let { JsonPathLite.text(item, it) },
                durationMs =
                    map.durationMs?.let {
                        JsonPathLite.text(item, it)
                    }?.toLongOrNull()?.times(map.durationMultiplier),
                externalIds = listOf(ExternalId(manifest.id, trackId)),
            )
        }
    }
}
