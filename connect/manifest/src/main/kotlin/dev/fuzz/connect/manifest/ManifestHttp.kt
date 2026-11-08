package dev.fuzz.connect.manifest

import dev.fuzz.core.domain.plugin.PluginResult
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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.IOException
import java.util.Base64

/**
 * Общий HTTP-слой подключений по манифесту: только домен `baseUrl`, авторизация из [secret],
 * ограничение размера ответа и частоты запросов. 404 даёт NOT_FOUND, 401/403 — UNAUTHORIZED.
 */
internal class ManifestHttp(
    private val client: HttpClient,
    private val manifest: Manifest,
    private val secret: String?,
    private val limiter: RateLimiter,
) {
    suspend fun getJson(
        path: String,
        query: Map<String, String>,
        vars: Map<String, String>,
    ): PluginResult<JsonElement> {
        val result =
            safeCall {
                limiter.run {
                    val response =
                        client.get(manifest.baseUrl.trimEnd('/') + path) {
                            query.forEach { (key, template) -> parameter(key, substitute(template, vars)) }
                            authorize()
                        }
                    when (response.status) {
                        HttpStatusCode.OK -> {
                            parse(response.bodyAsText())
                        }

                        HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> {
                            PluginResult.Failure(
                                PluginResult.Kind.UNAUTHORIZED,
                            )
                        }

                        HttpStatusCode.NotFound -> {
                            PluginResult.Failure(PluginResult.Kind.NOT_FOUND)
                        }

                        HttpStatusCode.TooManyRequests -> {
                            PluginResult.Failure(PluginResult.Kind.RATE_LIMITED)
                        }

                        else -> {
                            throw IOException("HTTP ${response.status.value}")
                        }
                    }
                }
            }
        return when (result) {
            is PluginResult.Ok -> result.value
            is PluginResult.Failure -> result
        }
    }

    /** Подстановки для запросов о треке. */
    fun trackVars(track: TrackRef): Map<String, String> =
        mapOf(
            "title" to track.title,
            "artist" to track.artist,
            "album" to track.album.orEmpty(),
            "durationSec" to track.durationMs?.div(MS_IN_SECOND)?.toString().orEmpty(),
            "id" to track.externalIds.firstOrNull { it.provider == manifest.id }?.id.orEmpty(),
        )

    private fun parse(text: String): PluginResult<JsonElement> {
        if (text.length > MAX_BODY_CHARS) throw IOException("Ответ слишком большой")
        return try {
            PluginResult.Ok(Json.parseToJsonElement(text))
        } catch (e: SerializationException) {
            PluginResult.Failure(PluginResult.Kind.PARSE, "Ответ не JSON: ${e.message?.lineSequence()?.first()}")
        }
    }

    private fun substitute(template: String, vars: Map<String, String>): String =
        PLACEHOLDER.replace(template) { m -> vars[m.groupValues[1]] ?: m.value }

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
        const val MAX_BODY_CHARS = 2_000_000
        const val MS_IN_SECOND = 1_000L
        val PLACEHOLDER = Regex("""\{([A-Za-z]+)}""")
    }
}
