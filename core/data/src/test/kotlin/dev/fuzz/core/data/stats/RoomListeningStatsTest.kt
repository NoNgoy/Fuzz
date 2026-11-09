package dev.fuzz.core.data.stats

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.fuzz.core.data.sync.Clock
import dev.fuzz.core.database.FuzzDatabase
import dev.fuzz.core.database.entity.ListenEntity
import dev.fuzz.core.database.entity.TrackEntity
import dev.fuzz.core.domain.stats.ListeningStats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class RoomListeningStatsTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FuzzDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val now = TimeUnit.DAYS.toMillis(100)
    private val clock =
        object : Clock() {
            override fun nowMs() = now
        }

    private fun daysAgo(days: Long) = now - TimeUnit.DAYS.toMillis(days)

    @After
    fun tearDown() = db.close()

    private fun track(
        id: String,
        artist: String,
        addedAt: Long,
        hidden: Boolean = false,
    ) = TrackEntity(
        id = id,
        title = id,
        artistName = artist,
        albumTitle = null,
        durationMs = 1,
        playbackUri = "content://$id",
        artworkUri = null,
        trackNumber = null,
        year = null,
        format = null,
        addedAt = addedAt,
        sourceId = "local",
        updatedAt = 0,
        hidden = hidden,
    )

    private fun listen(
        track: String,
        startedAt: Long,
        playedMs: Long,
    ) = ListenEntity(trackId = track, startedAt = startedAt, playedMs = playedMs, completed = true)

    @Test
    fun `counts the last thirty days only`() =
        runTest {
            db.trackDao().upsert(
                listOf(
                    track("old", "Кино", daysAgo(90)),
                    track("new1", "Кино", daysAgo(3)),
                    track("new2", "Сплин", daysAgo(2)),
                    track("hidden", "Земфира", daysAgo(1), hidden = true),
                ),
            )
            db.listenDao().insert(listen("new1", daysAgo(1), playedMs = 3_600_000))
            db.listenDao().insert(listen("old", daysAgo(40), playedMs = 9_000_000))

            val stats = RoomListeningStats(db.statsDao(), clock).observeLastMonth().first()

            // Кино уже было в коллекции, поэтому новый артист только Сплин; скрытый трек не считается.
            assertEquals(ListeningStats(listenedMs = 3_600_000, newTracks = 2, newArtists = 1), stats)
        }
}
