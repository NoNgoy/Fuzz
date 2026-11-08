package dev.fuzz.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.fuzz.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE deleted = 0")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id IN (:ids) AND deleted = 0")
    suspend fun getByIds(ids: List<String>): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE source_id = :sourceId")
    suspend fun getBySource(sourceId: String): List<TrackEntity>

    @Upsert
    suspend fun upsert(tracks: List<TrackEntity>)

    @Query("UPDATE tracks SET deleted = 1, updated_at = :now WHERE id IN (:ids)")
    suspend fun markDeleted(
        ids: List<String>,
        now: Long,
    )
}
