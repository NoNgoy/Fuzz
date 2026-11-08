package dev.fuzz.importer.formats

import dev.fuzz.core.domain.plugin.PlaylistImporter
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.TrackRef
import java.io.InputStream

/** Простой текст: строка — трек, «Исполнитель — Название». Удобно для списков из заметок или чатов. */
class TextImporter : PlaylistImporter {
    override val id = "txt"
    override val displayName = "Текст"
    override val formats = setOf("txt")

    override suspend fun import(input: InputStream): PluginResult<List<TrackRef>> =
        PluginResult.Ok(
            input
                .readTextGuessingCharset()
                .lineSequence()
                .map { it.trim().removePrefix("•").removePrefix("-").removePrefix("*").trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { parseArtistTitle(it) }
                .toList(),
        )
}
