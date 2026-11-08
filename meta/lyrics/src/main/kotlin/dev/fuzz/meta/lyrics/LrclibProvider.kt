package dev.fuzz.meta.lyrics

import dev.fuzz.core.domain.plugin.Lyrics
import dev.fuzz.core.domain.plugin.LyricsLine
import dev.fuzz.core.domain.plugin.LyricsProvider
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.core.network.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.io.IOException

/**
 * LRCLIB: открытый сервис синхронных текстов. Сначала берутся синхронные строки (LRC),
 * иначе обычный текст. Клиент должен иметь ContentNegotiation с JSON (ignoreUnknownKeys = true).
 */
class LrclibProvider(
    private val client: HttpClient,
    private val userAgent: String,
    private val baseUrl: String = "https://lrclib.net/api",
) : LyricsProvider {
    override val id = "lrclib"
    override val displayName = "LRCLIB"

    override suspend fun lyrics(track: TrackRef): PluginResult<Lyrics> {
        val result =
            safeCall {
                val response =
                    client.get("$baseUrl/get") {
                        header(HttpHeaders.UserAgent, userAgent)
                        parameter("artist_name", track.artist)
                        parameter("track_name", track.title)
                        track.album?.let { parameter("album_name", it) }
                        track.durationMs?.let { parameter("duration", it / MS_IN_SECOND) }
                    }
                when (response.status) {
                    HttpStatusCode.OK -> response.body<LrclibResponse>()
                    HttpStatusCode.NotFound -> null
                    else -> throw IOException("LRCLIB HTTP ${response.status.value}")
                }
            }
        return when (result) {
            is PluginResult.Failure -> {
                result
            }

            is PluginResult.Ok -> {
                result.value?.toLyrics()?.let { PluginResult.Ok(it) }
                    ?: PluginResult.Failure(PluginResult.Kind.NOT_FOUND)
            }
        }
    }

    private fun LrclibResponse.toLyrics(): Lyrics? {
        val synced = syncedLyrics?.takeIf { it.isNotBlank() }?.let(LrcParser::parse).orEmpty()
        if (synced.isNotEmpty()) return Lyrics(synced, synced = true)
        val plain = plainLyrics?.takeIf { it.isNotBlank() } ?: return null
        return Lyrics(plain.lines().map { LyricsLine(null, it) }, synced = false)
    }

    private companion object {
        const val MS_IN_SECOND = 1_000L
    }
}
