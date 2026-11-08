package dev.fuzz.connect.manifest

import dev.fuzz.core.domain.plugin.LrcParser
import dev.fuzz.core.domain.plugin.Lyrics
import dev.fuzz.core.domain.plugin.LyricsLine
import dev.fuzz.core.domain.plugin.LyricsProvider
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.TrackRef
import kotlinx.serialization.json.JsonElement

/** Тексты песен из своего API по [LyricsSpec]. Синхронный текст предпочтительнее обычного. */
class ManifestLyricsProvider internal constructor(
    manifest: Manifest,
    private val spec: LyricsSpec,
    private val http: ManifestHttp,
) : LyricsProvider {
    override val id = manifest.id
    override val displayName = manifest.name

    override suspend fun lyrics(track: TrackRef): PluginResult<Lyrics> {
        val root =
            when (val result = http.getJson(spec.path, spec.query, http.trackVars(track))) {
                is PluginResult.Failure -> return result
                is PluginResult.Ok -> result.value
            }
        return toLyrics(root)?.let { PluginResult.Ok(it) }
            ?: PluginResult.Failure(PluginResult.Kind.NOT_FOUND, "no lyrics")
    }

    private fun toLyrics(root: JsonElement): Lyrics? {
        val synced = spec.synced?.let { JsonPathLite.text(root, it) }?.let(LrcParser::parse).orEmpty()
        if (synced.isNotEmpty()) return Lyrics(synced, synced = true)
        return spec.plain?.let { JsonPathLite.text(root, it) }
            ?.lines()?.map { LyricsLine(timeMs = null, text = it.trim()) }
            ?.takeIf { lines -> lines.any { it.text.isNotEmpty() } }
            ?.let { Lyrics(it, synced = false) }
    }
}
