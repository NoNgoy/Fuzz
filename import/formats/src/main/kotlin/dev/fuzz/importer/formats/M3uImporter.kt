package dev.fuzz.importer.formats

import dev.fuzz.core.domain.plugin.PlaylistImporter
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.TrackRef
import java.io.InputStream

/** M3U и M3U8: берём #EXTINF, а без него имя файла. Кодировка UTF-8 (для .m3u из Windows бывает CP1251). */
class M3uImporter : PlaylistImporter {
    override val id = "m3u"
    override val displayName = "M3U"
    override val formats = setOf("m3u", "m3u8")

    override suspend fun import(input: InputStream): PluginResult<List<TrackRef>> {
        val lines = input.readTextGuessingCharset().lineSequence().map { it.trim() }
        val tracks = mutableListOf<TrackRef>()
        var pending: TrackRef? = null
        for (line in lines.filter { it.isNotEmpty() }) {
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                pending = parseExtInf(line)
            } else if (!line.startsWith("#")) {
                (pending ?: parseArtistTitle(fileName(line)))?.let { tracks += it }
                pending = null
            }
        }
        pending?.let { tracks += it }
        return PluginResult.Ok(tracks)
    }

    private fun parseExtInf(line: String): TrackRef? {
        val info = line.substringAfter(':', "")
        val seconds = info.substringBefore(',').substringBefore(' ').toLongOrNull()
        val display = info.substringAfter(',', "")
        return parseArtistTitle(display, durationMs = seconds?.takeIf { it > 0 }?.times(MS))
    }

    private companion object {
        const val MS = 1000L
    }
}
