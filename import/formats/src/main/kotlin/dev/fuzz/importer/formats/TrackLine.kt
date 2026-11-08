package dev.fuzz.importer.formats

import dev.fuzz.core.domain.plugin.TrackRef

private val SEPARATORS = listOf(" — ", " – ", " - ", " -- ")
private val LEADING_NUMBER = Regex("^\\s*\\d{1,3}\\s*[.\\-_)]\\s*")
private val AUDIO_EXTENSION = Regex("\\.(mp3|flac|m4a|aac|ogg|opus|wav|wma|ape|alac|dsf)$", RegexOption.IGNORE_CASE)

/**
 * «Исполнитель — Название» в [TrackRef]. Без разделителя вся строка считается названием.
 * Подходит и для строк #EXTINF, и для имён файлов («03 - Кино - Кукушка.flac»).
 */
internal fun parseArtistTitle(
    raw: String,
    durationMs: Long? = null,
): TrackRef? {
    val line = raw.trim().replace(AUDIO_EXTENSION, "").replace(LEADING_NUMBER, "").trim()
    val separator = SEPARATORS.firstOrNull { line.contains(it) }
    val artist = separator?.let { line.substringBefore(it).trim() }.orEmpty()
    val title = separator?.let { line.substringAfter(it).trim() } ?: line
    return when {
        line.isEmpty() -> null
        title.isEmpty() -> TrackRef(title = artist, artist = "", durationMs = durationMs)
        else -> TrackRef(title = title, artist = artist, durationMs = durationMs)
    }
}

/** Имя файла из пути или URL: «/sdcard/Music/Кино - Кукушка.flac» → «Кино - Кукушка.flac». */
internal fun fileName(path: String): String =
    java.net.URLDecoder
        .decode(path.replace("+", "%2B"), Charsets.UTF_8)
        .substringAfterLast('/')
        .substringAfterLast('\\')
