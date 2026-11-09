package dev.fuzz.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Запросы умных подборок. Только чтение. */
@Dao
interface SmartPlaylistDao {
    @Query(
        """
        SELECT t.id FROM listen_events l JOIN tracks t ON t.id = l.track_id
        WHERE l.started_at >= :sinceMs AND t.deleted = 0 AND t.hidden = 0
        GROUP BY t.id HAVING COUNT(*) >= :minPlays
        ORDER BY COUNT(*) DESC, MAX(l.started_at) DESC LIMIT :limit
        """,
    )
    fun observeOnRepeat(
        sinceMs: Long,
        minPlays: Int,
        limit: Int,
    ): Flow<List<String>>

    @Query(
        """
        SELECT t.id FROM track_reactions r JOIN tracks t ON t.id = r.track_id
        WHERE r.reaction = 1 AND t.deleted = 0 AND t.hidden = 0
            AND NOT EXISTS (SELECT 1 FROM listen_events l WHERE l.track_id = t.id AND l.started_at >= :sinceMs)
        ORDER BY r.updated_at ASC LIMIT :limit
        """,
    )
    fun observeForgotten(
        sinceMs: Long,
        limit: Int,
    ): Flow<List<String>>

    @Query(
        """
        SELECT id FROM tracks WHERE deleted = 0 AND hidden = 0 AND added_at >= :sinceMs
        ORDER BY added_at DESC LIMIT :limit
        """,
    )
    fun observeRecentlyAdded(
        sinceMs: Long,
        limit: Int,
    ): Flow<List<String>>

    @Query(
        """
        SELECT t.id FROM tracks t WHERE t.deleted = 0 AND t.hidden = 0
            AND NOT EXISTS (SELECT 1 FROM listen_events l WHERE l.track_id = t.id)
        ORDER BY t.added_at DESC LIMIT :limit
        """,
    )
    fun observeNeverPlayed(limit: Int): Flow<List<String>>
}
