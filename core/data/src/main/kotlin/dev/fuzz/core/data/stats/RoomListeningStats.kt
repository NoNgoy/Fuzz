package dev.fuzz.core.data.stats

import dev.fuzz.core.data.sync.Clock
import dev.fuzz.core.database.dao.StatsDao
import dev.fuzz.core.domain.stats.ListeningStats
import dev.fuzz.core.domain.stats.ListeningStatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.concurrent.TimeUnit
import javax.inject.Inject

internal class RoomListeningStats
    @Inject
    constructor(
        private val dao: StatsDao,
        private val clock: Clock,
    ) : ListeningStatsRepository {
        override fun observeLastMonth(): Flow<ListeningStats> {
            val since = clock.nowMs() - MONTH_MS
            return combine(
                dao.observePlayedMs(since),
                dao.observeNewTracks(since),
                dao.observeNewArtists(since),
            ) { played, tracks, artists -> ListeningStats(played, tracks, artists) }
        }

        private companion object {
            val MONTH_MS = TimeUnit.DAYS.toMillis(30)
        }
    }
