package dev.fuzz.core.data.sync

import androidx.room.withTransaction
import dev.fuzz.core.database.FuzzDatabase
import dev.fuzz.core.database.dao.TrackDao
import dev.fuzz.core.database.entity.TrackEntity
import dev.fuzz.core.domain.library.LocalLibraryStore
import dev.fuzz.core.domain.library.ScannedTrack
import dev.fuzz.core.domain.library.SyncStats
import javax.inject.Inject

internal class RoomLocalLibraryStore
    @Inject
    constructor(
        private val db: FuzzDatabase,
        private val dao: TrackDao,
        private val clock: Clock,
    ) : LocalLibraryStore {
        override suspend fun replaceLocal(tracks: List<ScannedTrack>): SyncStats =
            db.withTransaction {
                val now = clock.nowMs()
                val existing = dao.getBySource(LOCAL_SOURCE).associateBy { it.id }
                val scanned = tracks.associateBy { it.id }
                var added = 0
                var updated = 0
                val changed =
                    tracks.mapNotNull { track ->
                        val old = existing[track.id]
                        val entity = track.toEntity(now)
                        when {
                            old == null -> entity.also { added++ }
                            old.deleted || old.sameContentAs(entity).not() -> entity.also { updated++ }
                            else -> null
                        }
                    }
                if (changed.isNotEmpty()) dao.upsert(changed)
                val gone = existing.values.filter { !it.deleted && it.id !in scanned }.map { it.id }
                gone.chunked(SQLITE_MAX_ARGS).forEach { dao.markDeleted(it, now) }
                SyncStats(added = added, updated = updated, removed = gone.size)
            }

        private fun TrackEntity.sameContentAs(other: TrackEntity): Boolean =
            copy(updatedAt = 0, deleted = false) == other.copy(updatedAt = 0, deleted = false)

        private fun ScannedTrack.toEntity(now: Long) =
            TrackEntity(
                id = id,
                title = title,
                artistName = artistName,
                albumTitle = albumTitle,
                durationMs = durationMs,
                playbackUri = playbackUri,
                artworkUri = artworkUri,
                trackNumber = trackNumber,
                year = year,
                format = format,
                addedAt = addedAtMs,
                sourceId = LOCAL_SOURCE,
                updatedAt = now,
            )

        companion object {
            const val LOCAL_SOURCE = "local"
            private const val SQLITE_MAX_ARGS = 900
        }
    }
