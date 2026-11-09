package dev.fuzz.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.fuzz.core.database.entity.ListenEntity
import dev.fuzz.core.database.entity.PlaylistEntity
import dev.fuzz.core.database.entity.PlaylistTrackEntity
import dev.fuzz.core.database.entity.ReactionEntity
import dev.fuzz.core.database.entity.SavedRefEntity
import dev.fuzz.core.database.entity.TrackEntity

/** Чтение и запись всего, что попадает в резервную копию (раздел 3.5 ТЗ). */
@Dao
interface BackupDao {
    @Query("SELECT * FROM tracks WHERE deleted = 0")
    suspend fun tracks(): List<TrackEntity>

    @Query("SELECT * FROM saved_refs")
    suspend fun savedRefs(): List<SavedRefEntity>

    @Query("SELECT * FROM track_reactions")
    suspend fun reactions(): List<ReactionEntity>

    @Query("SELECT * FROM playlists WHERE deleted = 0")
    suspend fun playlists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_tracks ORDER BY playlist_id, position")
    suspend fun playlistEntries(): List<PlaylistTrackEntity>

    @Query("SELECT * FROM listen_events ORDER BY started_at")
    suspend fun listens(): List<ListenEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSavedRefs(refs: List<SavedRefEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putReactions(reactions: List<ReactionEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntries(entries: List<PlaylistTrackEntity>)

    @Insert
    suspend fun insertListens(listens: List<ListenEntity>)

    /** Плейлист и его треки одной транзакцией; плейлист с тем же id уже есть — ничего не меняется. */
    @Transaction
    suspend fun restorePlaylist(
        playlist: PlaylistEntity,
        entries: List<PlaylistTrackEntity>,
    ): Boolean {
        if (insertPlaylist(playlist) == -1L) return false
        insertEntries(entries)
        return true
    }
}
