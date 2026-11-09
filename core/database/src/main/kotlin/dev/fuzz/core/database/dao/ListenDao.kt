package dev.fuzz.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.fuzz.core.database.entity.ListenEntity

@Dao
interface ListenDao {
    @Insert
    suspend fun insert(entity: ListenEntity)

    @Query("SELECT * FROM listen_events WHERE started_at >= :sinceMs ORDER BY started_at")
    suspend fun since(sinceMs: Long): List<ListenEntity>

    /** Старше [beforeMs] профилю уже не нужно: вклад затухает за месяц. */
    @Query("DELETE FROM listen_events WHERE started_at < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long)

    @Query("DELETE FROM listen_events")
    suspend fun clearAll()
}
