package dev.fuzz.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.fuzz.core.database.entity.WishEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WishDao {
    @Query("SELECT * FROM wishlist ORDER BY added_at DESC, id")
    fun observeAll(): Flow<List<WishEntity>>

    @Query("SELECT * FROM wishlist")
    suspend fun all(): List<WishEntity>

    @Query("SELECT COUNT(*) FROM wishlist")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insert(wishes: List<WishEntity>)

    @Query("DELETE FROM wishlist WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM playlists WHERE id = :id AND deleted = 0")
    suspend fun playlistAlive(id: String): Int
}
