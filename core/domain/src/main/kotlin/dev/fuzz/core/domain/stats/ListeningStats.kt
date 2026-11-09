package dev.fuzz.core.domain.stats

import kotlinx.coroutines.flow.Flow

/** Цифры профиля за последние 30 дней. */
data class ListeningStats(
    val listenedMs: Long = 0,
    val newTracks: Int = 0,
    val newArtists: Int = 0,
)

interface ListeningStatsRepository {
    fun observeLastMonth(): Flow<ListeningStats>

    /** Стирает историю прослушиваний: статистика обнуляется, волна забывает, что звучало. */
    suspend fun clearHistory()
}
