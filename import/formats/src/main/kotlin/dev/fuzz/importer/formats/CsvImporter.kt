package dev.fuzz.importer.formats

import dev.fuzz.core.domain.plugin.PlaylistImporter
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.TrackRef
import java.io.InputStream

/**
 * CSV с заголовком: Exportify (Spotify), выгрузки Яндекс Музыки сторонними сервисами, таблицы руками.
 * Колонки узнаются по названию на русском и английском, разделитель — запятая, точка с запятой или табуляция.
 * Без узнаваемого заголовка каждая строка читается как «Исполнитель — Название».
 */
class CsvImporter : PlaylistImporter {
    override val id = "csv"
    override val displayName = "CSV"
    override val formats = setOf("csv", "tsv")

    override suspend fun import(input: InputStream): PluginResult<List<TrackRef>> {
        val text = input.readTextGuessingCharset()
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return PluginResult.Ok(emptyList())
        val delimiter = listOf('\t', ';', ',').maxBy { d -> firstLine.count { it == d } }
        val rows = parseCsv(text, delimiter).filter { row -> row.any { it.isNotBlank() } }
        if (rows.isEmpty()) return PluginResult.Ok(emptyList())
        val columns = Columns.detect(rows.first())
        val tracks =
            if (columns == null) {
                rows.mapNotNull { row -> parseArtistTitle(row.joinToString(" - ") { it.trim() }.trim(' ', '-')) }
            } else {
                rows.drop(1).mapNotNull(columns::toRef)
            }
        return PluginResult.Ok(tracks)
    }

    private class Columns(
        val title: Int,
        val artist: Int?,
        val album: Int?,
        val duration: Int?,
    ) {
        fun toRef(row: List<String>): TrackRef? {
            val title = row.getOrNull(title)?.trim().orEmpty()
            if (title.isEmpty()) return null
            return TrackRef(
                title = title,
                artist = artist?.let { row.getOrNull(it) }?.trim().orEmpty(),
                album = album?.let { row.getOrNull(it) }?.trim()?.ifEmpty { null },
                durationMs = duration?.let { row.getOrNull(it) }?.let(::parseDuration),
            )
        }

        companion object {
            private val TITLE = setOf("track name", "track", "title", "name", "song", "название", "трек", "песня")
            private val ARTIST =
                setOf("artist name(s)", "artist name", "artist", "artists", "исполнитель", "исполнители", "артист")
            private val ALBUM = setOf("album name", "album", "альбом")
            private val DURATION = setOf("duration (ms)", "duration_ms", "duration", "length", "длительность")

            fun detect(header: List<String>): Columns? {
                val names = header.map { it.trim().lowercase() }

                fun find(aliases: Set<String>) = names.indexOfFirst { it in aliases }.takeIf { it >= 0 }
                val title = find(TITLE) ?: return null
                return Columns(title, find(ARTIST), find(ALBUM), find(DURATION))
            }
        }
    }
}

/** «238000» (мс), «238» (с) или «3:58». */
internal fun parseDuration(raw: String): Long? {
    val value = raw.trim()
    if (value.contains(':')) {
        val parts = value.split(':').map { it.toLongOrNull() }
        if (parts.any { it == null }) return null
        return parts.fold(0L) { acc, part -> acc * SECONDS_IN_MINUTE + part!! } * MS
    }
    val number = value.toLongOrNull()?.takeIf { it > 0 }
    return number?.let { if (it > MAX_SECONDS) it else it * MS }
}

/** RFC 4180: кавычки, удвоенные кавычки и переводы строк внутри кавычек. */
internal fun parseCsv(
    text: String,
    delimiter: Char,
): List<List<String>> = CsvReader(text, delimiter).readAll()

private class CsvReader(
    private val text: String,
    private val delimiter: Char,
) {
    private val rows = mutableListOf<List<String>>()
    private var row = mutableListOf<String>()
    private val field = StringBuilder()
    private var quoted = false
    private var i = 0

    fun readAll(): List<List<String>> {
        while (i < text.length) {
            if (quoted) readQuoted(text[i]) else readPlain(text[i])
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    private fun readQuoted(c: Char) {
        when {
            c == '"' && text.getOrNull(i + 1) == '"' -> {
                field.append('"')
                i++
            }

            c == '"' -> {
                quoted = false
            }

            else -> {
                field.append(c)
            }
        }
    }

    private fun readPlain(c: Char) {
        when (c) {
            '"' -> {
                quoted = true
            }

            delimiter -> {
                endField()
            }

            '\n' -> {
                endRow()
            }

            '\r' -> {
                if (text.getOrNull(i + 1) == '\n') i++
                endRow()
            }

            else -> {
                field.append(c)
            }
        }
    }

    private fun endField() {
        row += field.toString()
        field.clear()
    }

    private fun endRow() {
        endField()
        rows += row
        row = mutableListOf()
    }
}

private const val MS = 1000L
private const val SECONDS_IN_MINUTE = 60L

/** Больше часа в секундах не бывает у трека в плейлисте: значит, число в миллисекундах. */
private const val MAX_SECONDS = 3600L
