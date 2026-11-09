package dev.fuzz.core.data.library

import dev.fuzz.core.data.sync.Clock
import dev.fuzz.core.database.dao.SmartPlaylistDao
import dev.fuzz.core.domain.library.SmartPlaylist
import dev.fuzz.core.domain.library.SmartPlaylists
import dev.fuzz.core.model.TrackId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject

internal class RoomSmartPlaylists
    @Inject
    constructor(
        private val dao: SmartPlaylistDao,
        private val clock: Clock,
    ) : SmartPlaylists {
        override fun observe(playlist: SmartPlaylist): Flow<List<TrackId>> {
            val now = clock.nowMs()
            val ids =
                when (playlist) {
                    SmartPlaylist.OnRepeat -> dao.observeOnRepeat(now - MONTH_MS, MIN_REPEAT_PLAYS, LIMIT)
                    SmartPlaylist.Forgotten -> dao.observeForgotten(now - FORGOTTEN_MS, LIMIT)
                    SmartPlaylist.RecentlyAdded -> dao.observeRecentlyAdded(now - MONTH_MS, LIMIT)
                    SmartPlaylist.NeverPlayed -> dao.observeNeverPlayed(LIMIT)
                }
            return ids.map { list -> list.map(::TrackId) }
        }

        private companion object {
            val MONTH_MS = TimeUnit.DAYS.toMillis(30)
            val FORGOTTEN_MS = TimeUnit.DAYS.toMillis(60)
            const val MIN_REPEAT_PLAYS = 2
            const val LIMIT = 100
        }
    }
