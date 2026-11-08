package dev.fuzz.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import dev.fuzz.core.database.entity.PlaylistEntity
import dev.fuzz.core.database.entity.PlaylistTrackEntity
import dev.fuzz.core.database.entity.PlaylistWithCount
import dev.fuzz.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.id, p.name, p.updated_at,
            (SELECT COUNT(*) FROM playlist_tracks pt JOIN tracks t ON t.id = pt.track_id
             WHERE pt.playlist_id = p.id AND t.deleted = 0) AS track_count
        FROM playlists p WHERE p.deleted = 0 ORDER BY p.updated_at DESC
        """,
    )
    fun observeAll(): Flow<List<PlaylistWithCount>>

    @Query(
        """
        SELECT p.id, p.name, p.updated_at,
            (SELECT COUNT(*) FROM playlist_tracks pt JOIN tracks t ON t.id = pt.track_id
             WHERE pt.playlist_id = p.id AND t.deleted = 0) AS track_count
        FROM playlists p WHERE p.id = :id AND p.deleted = 0
        """,
    )
    fun observe(id: String): Flow<PlaylistWithCount?>

    @Query(
        """
        SELECT t.* FROM playlist_tracks pt JOIN tracks t ON t.id = pt.track_id
        WHERE pt.playlist_id = :id AND t.deleted = 0 ORDER BY pt.position
        """,
    )
    fun observeTracks(id: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlist_id = :id ORDER BY position")
    suspend fun entries(id: String): List<PlaylistTrackEntity>

    @Insert
    suspend fun insert(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET name = :name, updated_at = :now WHERE id = :id")
    suspend fun rename(
        id: String,
        name: String,
        now: Long,
    )

    @Query("UPDATE playlists SET deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun markDeleted(
        id: String,
        now: Long,
    )

    @Query("UPDATE playlists SET updated_at = :now WHERE id = :id")
    suspend fun touch(
        id: String,
        now: Long,
    )

    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :id")
    suspend fun clearEntries(id: String)

    @Insert
    suspend fun insertEntries(entries: List<PlaylistTrackEntity>)

    /** Переписывает порядок треков целиком: позиции остаются плотными. */
    @Transaction
    suspend fun replaceEntries(
        id: String,
        entries: List<PlaylistTrackEntity>,
        now: Long,
    ) {
        clearEntries(id)
        insertEntries(entries.mapIndexed { index, entry -> entry.copy(playlistId = id, position = index) })
        touch(id, now)
    }
}
