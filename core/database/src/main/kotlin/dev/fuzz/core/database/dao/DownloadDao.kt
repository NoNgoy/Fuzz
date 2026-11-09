package dev.fuzz.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.fuzz.core.database.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT track_id FROM downloads")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT * FROM downloads WHERE track_id = :trackId")
    suspend fun get(trackId: String): DownloadEntity?

    @Upsert
    suspend fun upsert(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE track_id = :trackId")
    suspend fun delete(trackId: String)
}
