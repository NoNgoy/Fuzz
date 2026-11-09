package dev.fuzz.connect.script

import dev.fuzz.core.domain.plugin.ExternalId
import dev.fuzz.core.domain.plugin.LrcParser
import dev.fuzz.core.domain.plugin.Lyrics
import dev.fuzz.core.domain.plugin.LyricsLine
import dev.fuzz.core.domain.plugin.LyricsProvider
import dev.fuzz.core.domain.plugin.MusicSource
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.SearchResult
import dev.fuzz.core.domain.plugin.SourceCapability
import dev.fuzz.core.domain.plugin.TrackRef
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.Closeable

/**
 * Скриптовый плагин (раздел 8.4 ТЗ). Скрипт объявляет глобальный объект `plugin`:
 *
 * ```js
 * const plugin = {
 *   async search(query, limit) { return [{ id, title, artist, album, durationMs }] },
 *   async streamUrl(id) { return "https://..." },
 *   async lyrics(track) { return { synced: "[00:01.00]..." } }, // или { plain }, или null
 * };
 * ```
 *
 * Сеть только через `fuzz.http.get(url, headers)` → `{ status, body, error }` и только к доменам из
 * [ScriptHeader.domains]. Сбой, таймаут или мусор в ответе скрипта дают [PluginResult.Failure].
 */
class ScriptConnection(
    client: HttpClient,
    val header: ScriptHeader,
    code: String,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val limits: ScriptLimits = ScriptLimits(),
    log: (String) -> Unit = {},
) : Closeable {
    private val http = ScriptHttp(client, header.domains.map { it.lowercase() }, MIN_INTERVAL_MS)
    private val runtime = ScriptRuntime(code, http, dispatcher, limits, log)

    val source: MusicSource? = if (ScriptHeader.CAP_SOURCE in header.capabilities) Source() else null
    val lyrics: LyricsProvider? = if (ScriptHeader.CAP_LYRICS in header.capabilities) Lyricist() else null

    override fun close() = runtime.close()

    private inner class Source : MusicSource {
        override val id = header.id
        override val displayName = header.name
        override val capabilities = setOf(SourceCapability.STREAM)

        override suspend fun search(query: String, limit: Int): PluginResult<SearchResult> =
            call<List<ScriptTrack>>("plugin.search(${json.encodeToString(query)}, $limit)").map { list ->
                SearchResult(tracks = list.orEmpty().take(limit).mapNotNull { it.toRef() })
            }

        override suspend fun playbackUri(track: TrackRef): PluginResult<String> {
            val trackId =
                track.externalIds.firstOrNull { it.provider == header.id }?.id
                    ?: return PluginResult.Failure(PluginResult.Kind.NOT_FOUND, "no ${header.id} id")
            return call<String>("plugin.streamUrl(${json.encodeToString(trackId)})").flatMap { url ->
                when {
                    url == null -> PluginResult.Failure(PluginResult.Kind.NOT_FOUND)
                    http.allowed(url) -> PluginResult.Ok(url)
                    else -> PluginResult.Failure(PluginResult.Kind.PARSE, "Ссылка вне разрешённых доменов")
                }
            }
        }
    }

    private inner class Lyricist : LyricsProvider {
        override val id = header.id
        override val displayName = header.name

        override suspend fun lyrics(track: TrackRef): PluginResult<Lyrics> {
            val arg =
                json.encodeToString(
                    ScriptTrack(
                        id = track.externalIds.firstOrNull { it.provider == header.id }?.id.orEmpty(),
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        durationMs = track.durationMs,
                    ),
                )
            return call<ScriptLyrics>("plugin.lyrics($arg)").flatMap { l ->
                l?.toLyrics()?.let { PluginResult.Ok(it) } ?: PluginResult.Failure(PluginResult.Kind.NOT_FOUND)
            }
        }
    }

    private suspend inline fun <reified T> call(expression: String): PluginResult<T?> =
        try {
            val text = withTimeout(limits.timeoutMs + TIMEOUT_GRACE_MS) { runtime.callJson(expression) }
            PluginResult.Ok(text?.takeIf { it != "null" && it != "undefined" }?.let { json.decodeFromString<T>(it) })
        } catch (e: TimeoutCancellationException) {
            PluginResult.Failure(PluginResult.Kind.TIMEOUT, e.message)
        } catch (e: ScriptException) {
            PluginResult.Failure(PluginResult.Kind.PARSE, e.message)
        } catch (e: SerializationException) {
            PluginResult.Failure(PluginResult.Kind.PARSE, "Скрипт вернул не тот формат: ${e.message}")
        } catch (e: IllegalArgumentException) {
            PluginResult.Failure(PluginResult.Kind.PARSE, "Скрипт вернул не тот формат: ${e.message}")
        }

    private fun ScriptTrack.toRef(): TrackRef? =
        if (id.isBlank() || title.isBlank()) {
            null
        } else {
            TrackRef(title, artist, album, durationMs, listOf(ExternalId(header.id, id)))
        }

    private inline fun <T, R> PluginResult<T>.map(transform: (T) -> R): PluginResult<R> =
        when (this) {
            is PluginResult.Ok -> PluginResult.Ok(transform(value))
            is PluginResult.Failure -> this
        }

    private inline fun <T, R> PluginResult<T>.flatMap(transform: (T) -> PluginResult<R>): PluginResult<R> =
        when (this) {
            is PluginResult.Ok -> transform(value)
            is PluginResult.Failure -> this
        }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        const val MIN_INTERVAL_MS = 300L
        const val TIMEOUT_GRACE_MS = 2_000L
    }
}

@Serializable
internal data class ScriptTrack(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String? = null,
    val durationMs: Long? = null,
)

@Serializable
internal data class ScriptLyrics(val synced: String? = null, val plain: String? = null) {
    fun toLyrics(): Lyrics? {
        val lines = synced?.let(LrcParser::parse).orEmpty()
        if (lines.isNotEmpty()) return Lyrics(lines, synced = true)
        return plain?.takeIf {
            it.isNotBlank()
        }?.lines()?.map { LyricsLine(null, it.trim()) }?.let { Lyrics(it, false) }
    }
}
