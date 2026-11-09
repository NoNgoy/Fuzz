package dev.fuzz.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.fuzz.core.database.entity.SavedRefEntity

@Dao
interface SavedRefDao {
    @Query("SELECT * FROM saved_refs WHERE track_id = :trackId")
    suspend fun get(trackId: String): SavedRefEntity?

    @Upsert
    suspend fun upsert(ref: SavedRefEntity)
}
