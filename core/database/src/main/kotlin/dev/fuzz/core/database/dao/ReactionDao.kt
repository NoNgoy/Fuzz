package dev.fuzz.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.fuzz.core.database.entity.ReactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReactionDao {
    @Query("SELECT reaction FROM track_reactions WHERE track_id = :trackId")
    fun observe(trackId: String): Flow<Int?>

    @Query("SELECT track_id FROM track_reactions WHERE reaction = 1")
    fun observeLiked(): Flow<List<String>>

    @Upsert
    suspend fun upsert(entity: ReactionEntity)
}
