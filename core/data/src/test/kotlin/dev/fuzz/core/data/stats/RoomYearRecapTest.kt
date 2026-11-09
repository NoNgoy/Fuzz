package dev.fuzz.core.data.stats

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.fuzz.core.data.sync.Clock
import dev.fuzz.core.database.FuzzDatabase
import dev.fuzz.core.database.entity.ListenEntity
import dev.fuzz.core.database.entity.TrackEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class RoomYearRecapTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FuzzDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private fun at(
        year: Int,
        month: Int,
        day: Int = 10,
    ) = LocalDateTime.of(year, month, day, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private val clock =
        object : Clock() {
            override fun nowMs() = at(2026, 11, 9)
        }
    private val recap = RoomYearRecap(db.recapDao(), clock).apply { zone = ZoneOffset.UTC }

    @After
    fun tearDown() = db.close()

    private fun track(
        id: String,
        artist: String,
        addedAt: Long,
    ) = TrackEntity(
        id = id,
        title = "Песня $id",
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
    )

    private suspend fun listen(
        track: String,
        startedAt: Long,
        playedMs: Long = 200_000,
    ) = db.listenDao().insert(
        ListenEntity(trackId = track, startedAt = startedAt, playedMs = playedMs, completed = true),
    )

    @Test
    fun `counts only this year and ranks tracks, artists and months`() =
        runTest {
            db.trackDao().upsert(
                listOf(
                    track("a", "Кино", at(2025, 5)),
                    track("b", "Кино", at(2025, 5)),
                    track("c", "Сплин", at(2026, 3)),
                ),
            )
            listen("a", at(2025, 12, 31))
            repeat(3) { listen("c", at(2026, 3)) }
            listen("a", at(2026, 7))
            listen("b", at(2026, 7))
            listen("b", at(2026, 7, 11), playedMs = 1_000_000)
            listen("preview", at(2026, 8))

            val result = recap.currentYear()

            assertEquals(2026, result.year)
            assertEquals(7, result.plays)
            assertEquals(3 * 200_000L + 200_000 + 200_000 + 1_000_000 + 200_000, result.listenedMs)
            assertEquals(4, result.distinctTracks)
            assertEquals(listOf("c", "b", "a"), result.topTracks.map { it.id })
            assertEquals(listOf("Кино", "Сплин"), result.topArtists.map { it.name })
            assertEquals(7, result.topMonth)
            assertEquals(1, result.newArtists)
        }

    @Test
    fun `empty year has no numbers`() =
        runTest {
            assertTrue(recap.currentYear().isEmpty)
        }
}
