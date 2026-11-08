package dev.fuzz.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.fuzz.core.database.entity.SimilarityCacheEntity

@Dao
interface SimilarityCacheDao {
    @Query("SELECT * FROM similarity_cache WHERE kind = :kind AND `key` = :key")
    suspend fun get(kind: String, key: String): SimilarityCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: SimilarityCacheEntity)

    @Query("DELETE FROM similarity_cache WHERE stored_at < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long)
}
