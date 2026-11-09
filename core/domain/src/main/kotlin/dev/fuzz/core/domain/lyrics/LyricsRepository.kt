package dev.fuzz.core.domain.lyrics

import dev.fuzz.core.domain.plugin.Lyrics
import dev.fuzz.core.model.Track

sealed interface LyricsResult {
    data class Found(
        val lyrics: Lyrics,
        /** Откуда текст: LRCLIB или название своего API. */
        val source: String,
    ) : LyricsResult

    /** Все источники ответили, текста нет. */
    data object NotFound : LyricsResult

    /** Ни один источник не ответил: нет сети или сервисы недоступны. */
    data object Unavailable : LyricsResult
}

interface LyricsRepository {
    suspend fun lyrics(track: Track): LyricsResult
}
