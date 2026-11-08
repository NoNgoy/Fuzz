package dev.fuzz.core.data.playlist

import androidx.room.withTransaction
import dev.fuzz.core.data.library.toModel
import dev.fuzz.core.data.sync.Clock
import dev.fuzz.core.database.FuzzDatabase
import dev.fuzz.core.database.dao.PlaylistDao
import dev.fuzz.core.database.entity.PlaylistEntity
import dev.fuzz.core.database.entity.PlaylistTrackEntity
import dev.fuzz.core.database.entity.PlaylistWithCount
import dev.fuzz.core.domain.playlist.PlaylistRepository
import dev.fuzz.core.model.Playlist
import dev.fuzz.core.model.PlaylistId
import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class OfflinePlaylistRepository
    @Inject
    constructor(
        private val db: FuzzDatabase,
        private val dao: PlaylistDao,
        private val clock: Clock,
    ) : PlaylistRepository {
        override fun observePlaylists(): Flow<List<Playlist>> =
            dao.observeAll().map { list ->
                list.map { it.toModel() }
            }

        override fun observePlaylist(id: PlaylistId): Flow<Playlist?> = dao.observe(id.value).map { it?.toModel() }

        override fun observeTracks(id: PlaylistId): Flow<List<Track>> =
            dao.observeTracks(id.value).map { list -> list.map { it.toModel() } }

        override suspend fun create(name: String): PlaylistId {
            val id = PlaylistId.random()
            val now = clock.nowMs()
            dao.insert(PlaylistEntity(id = id.value, name = name.trim(), createdAt = now, updatedAt = now))
            return id
        }

        override suspend fun rename(
            id: PlaylistId,
            name: String,
        ) = dao.rename(id.value, name.trim(), clock.nowMs())

        override suspend fun delete(id: PlaylistId) = dao.markDeleted(id.value, clock.nowMs())

        override suspend fun addTracks(
            id: PlaylistId,
            tracks: List<TrackId>,
        ): Int =
            db.withTransaction {
                val now = clock.nowMs()
                val entries = dao.entries(id.value)
                val present = entries.mapTo(HashSet()) { it.trackId }
                val fresh = tracks.map { it.value }.distinct().filterNot { it in present }
                if (fresh.isNotEmpty()) {
                    dao.insertEntries(
                        fresh.mapIndexed { i, track -> PlaylistTrackEntity(id.value, entries.size + i, track, now) },
                    )
                    dao.touch(id.value, now)
                }
                fresh.size
            }

        override suspend fun removeTrack(
            id: PlaylistId,
            track: TrackId,
        ) {
            db.withTransaction {
                val entries = dao.entries(id.value)
                val kept = entries.filterNot { it.trackId == track.value }
                if (kept.size != entries.size) dao.replaceEntries(id.value, kept, clock.nowMs())
            }
        }

        override suspend fun moveTrack(
            id: PlaylistId,
            from: Int,
            to: Int,
        ) {
            db.withTransaction {
                val entries = dao.entries(id.value).toMutableList()
                if (from !in entries.indices || to !in entries.indices || from == to) return@withTransaction
                entries.add(to, entries.removeAt(from))
                dao.replaceEntries(id.value, entries, clock.nowMs())
            }
        }

        private fun PlaylistWithCount.toModel() =
            Playlist(id = PlaylistId(id), name = name, trackCount = trackCount, updatedAtMs = updatedAt)
    }
