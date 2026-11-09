package dev.fuzz.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Цифры для профиля. Только чтение, поэтому отдельно от DAO, которые пишут. */
@Dao
interface StatsDao {
    @Query("SELECT COALESCE(SUM(played_ms), 0) FROM listen_events WHERE started_at >= :sinceMs")
    fun observePlayedMs(sinceMs: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM tracks WHERE deleted = 0 AND hidden = 0 AND added_at >= :sinceMs")
    fun observeNewTracks(sinceMs: Long): Flow<Int>

    /** Артист новый, если самый ранний его трек в коллекции появился не раньше [sinceMs]. */
    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT artist_name FROM tracks WHERE deleted = 0 AND hidden = 0
            GROUP BY artist_name HAVING MIN(added_at) >= :sinceMs
        )
        """,
    )
    fun observeNewArtists(sinceMs: Long): Flow<Int>
}
