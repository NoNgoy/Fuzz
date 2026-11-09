package dev.fuzz.core.data.stats

import dev.fuzz.core.data.sync.Clock
import dev.fuzz.core.database.dao.RecapDao
import dev.fuzz.core.domain.stats.RecapArtist
import dev.fuzz.core.domain.stats.RecapTrack
import dev.fuzz.core.domain.stats.YearRecap
import dev.fuzz.core.domain.stats.YearRecapRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

internal class RoomYearRecap
    @Inject
    constructor(
        private val dao: RecapDao,
        private val clock: Clock,
    ) : YearRecapRepository {
        /** Часовой пояс телефона: год и месяцы считаются так, как их видит человек. */
        internal var zone: ZoneId = ZoneId.systemDefault()

        override suspend fun currentYear(): YearRecap {
            val year = Instant.ofEpochMilli(clock.nowMs()).atZone(zone).year
            return of(year)
        }

        internal suspend fun of(year: Int): YearRecap {
            val from = startOf(year)
            val to = startOf(year + 1)
            val listens = dao.listens(from, to)
            if (listens.isEmpty()) return YearRecap(year)
            val byMonth =
                listens
                    .groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).monthValue }
                    .mapValues { (_, month) -> month.sumOf { it.playedMs } }
            return YearRecap(
                year = year,
                listenedMs = listens.sumOf { it.playedMs },
                plays = listens.size,
                distinctTracks = dao.distinctTracks(from, to),
                topTracks =
                    dao.topTracks(from, to, TOP).map {
                        RecapTrack(it.id, it.title, it.artist, it.artworkUri, it.plays)
                    },
                topArtists = dao.topArtists(from, to, TOP).map { RecapArtist(it.name, it.plays, it.playedMs) },
                topMonth = byMonth.maxByOrNull { it.value }?.key,
                newArtists = dao.newArtists(from, to),
            )
        }

        private fun startOf(year: Int) = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()

        private companion object {
            const val TOP = 5
        }
    }
