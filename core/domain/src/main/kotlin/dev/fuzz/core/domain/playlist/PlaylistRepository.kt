package dev.fuzz.core.domain.playlist

import dev.fuzz.core.model.Playlist
import dev.fuzz.core.model.PlaylistId
import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    /** Плейлисты по дате изменения, свежие сверху. */
    fun observePlaylists(): Flow<List<Playlist>>

    fun observePlaylist(id: PlaylistId): Flow<Playlist?>

    /** Треки в порядке плейлиста. Скрытые (файл удалён) пропускаются. */
    fun observeTracks(id: PlaylistId): Flow<List<Track>>

    suspend fun create(name: String): PlaylistId

    suspend fun rename(
        id: PlaylistId,
        name: String,
    )

    suspend fun delete(id: PlaylistId)

    /** Добавляет в конец. Уже лежащие в плейлисте треки не дублируются. */
    suspend fun addTracks(
        id: PlaylistId,
        tracks: List<TrackId>,
    ): Int

    suspend fun removeTrack(
        id: PlaylistId,
        track: TrackId,
    )

    suspend fun moveTrack(
        id: PlaylistId,
        from: Int,
        to: Int,
    )
}
