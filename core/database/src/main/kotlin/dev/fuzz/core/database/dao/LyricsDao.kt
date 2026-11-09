package dev.fuzz.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.fuzz.core.database.entity.LyricsEntity

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE `key` = :key")
    suspend fun get(key: String): LyricsEntity?

    @Upsert
    suspend fun upsert(entity: LyricsEntity)

    @Query("SELECT COUNT(*) FROM lyrics")
    suspend fun count(): Int

    /** Оставляет [keep] самых свежих: тексты лёгкие, но бесконечно копить их незачем. */
    @Query("DELETE FROM lyrics WHERE `key` NOT IN (SELECT `key` FROM lyrics ORDER BY fetched_at DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}
