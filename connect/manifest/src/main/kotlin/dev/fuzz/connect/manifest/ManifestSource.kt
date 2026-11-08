package dev.fuzz.connect.manifest

import dev.fuzz.core.domain.plugin.ExternalId
import dev.fuzz.core.domain.plugin.MusicSource
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.SearchResult
import dev.fuzz.core.domain.plugin.SourceCapability
import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.core.network.RateLimiter
import dev.fuzz.core.network.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.util.Base64

/**
 * Подключение, собранное из [Manifest]: поиск треков и потоковая ссылка. Запросы идут только на домен
 * `baseUrl`, ответ режется по размеру, число запросов ограничено [limiter]. [secret] (ключ, токен или
 * `логин:пароль`) приходит из защищённого хранилища, в манифесте его нет.
 */
class ManifestSource(
    private val client: HttpClient,
    private val manifest: Manifest,
    private val secret: String? = null,
    private val limiter: RateLimiter = RateLimiter(MIN_INTERVAL_MS),
) : MusicSource {
    override val id = manifest.id
    override val displayName = manifest.name
    override val capabilities = setOf(SourceCapability.STREAM)

    override suspend fun search(query: String, limit: Int): PluginResult<SearchResult> {
        val spec = manifest.search
        val result =
            safeCall {
                limiter.run {
                    val response =
                        client.get(manifest.baseUrl.trimEnd('/') + spec.path) {
                            spec.query.forEach { (key, template) ->
                                parameter(key, template.replace("{query}", query).replace("{limit}", limit.toString()))
                            }
                            authorize()
                        }
                    if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                        return@run null
                    }
                    if (response.status != HttpStatusCode.OK) throw IOException("HTTP ${response.status.value}")
                    val text = response.bodyAsText()
                    if (text.length > MAX_BODY_CHARS) throw IOException("Ответ слишком большой")
                    try {
                        Json.parseToJsonElement(text)
                    } catch (e: SerializationException) {
                        throw IOException("Ответ не JSON", e)
                    }
                }
            }
        return when (result) {
            is PluginResult.Failure -> {
                result
            }

            is PluginResult.Ok -> {
                result.value?.let { PluginResult.Ok(SearchResult(tracks = extractTracks(it).take(limit))) }
                    ?: PluginResult.Failure(PluginResult.Kind.UNAUTHORIZED)
            }
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
            val trackId = text(item, map.id)
            val title = text(item, map.title)
            if (trackId == null || title == null) return@mapNotNull null
            TrackRef(
                title = title,
                artist = text(item, map.artist).orEmpty(),
                album = map.album?.let { text(item, it) },
                durationMs = map.durationMs?.let { text(item, it) }?.toLongOrNull()?.times(map.durationMultiplier),
                externalIds = listOf(ExternalId(manifest.id, trackId)),
            )
        }
    }

    private fun text(item: JsonElement, path: String): String? =
        (JsonPathLite.select(item, path).firstOrNull() as? JsonPrimitive)
            ?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() }

    private fun HttpRequestBuilder.authorize() {
        val key = secret ?: return
        when (manifest.auth.type) {
            "bearer" -> {
                header(HttpHeaders.Authorization, "Bearer $key")
            }

            "basic" -> {
                header(
                    HttpHeaders.Authorization,
                    "Basic " + Base64.getEncoder().encodeToString(key.toByteArray()),
                )
            }

            "apiKey" -> {
                if (manifest.auth.`in` ==
                    "header"
                ) {
                    header(manifest.auth.name, key)
                } else {
                    parameter(manifest.auth.name, key)
                }
            }
        }
    }

    private companion object {
        const val MIN_INTERVAL_MS = 300L
        const val MAX_BODY_CHARS = 2_000_000
    }
}
