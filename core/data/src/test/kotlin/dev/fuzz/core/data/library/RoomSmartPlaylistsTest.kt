package dev.fuzz.core.data.library

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.fuzz.core.data.sync.Clock
import dev.fuzz.core.database.FuzzDatabase
import dev.fuzz.core.database.entity.ListenEntity
import dev.fuzz.core.database.entity.ReactionEntity
import dev.fuzz.core.database.entity.TrackEntity
import dev.fuzz.core.domain.library.SmartPlaylist
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class RoomSmartPlaylistsTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FuzzDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val now = TimeUnit.DAYS.toMillis(400)
    private val smart =
        RoomSmartPlaylists(
            db.smartPlaylistDao(),
            object : Clock() {
                override fun nowMs() = now
            },
        )

    private fun daysAgo(days: Long) = now - TimeUnit.DAYS.toMillis(days)

    @After
    fun tearDown() = db.close()

    private fun track(
        id: String,
        addedDaysAgo: Long,
        hidden: Boolean = false,
    ) = TrackEntity(
        id = id,
        title = id,
        artistName = "Кино",
        albumTitle = null,
        durationMs = 1,
        playbackUri = "content://$id",
        artworkUri = null,
        trackNumber = null,
        year = null,
        format = null,
        addedAt = daysAgo(addedDaysAgo),
        sourceId = "local",
        updatedAt = 0,
        hidden = hidden,
    )

    private suspend fun listen(
        id: String,
        daysAgo: Long,
    ) = db.listenDao().insert(ListenEntity(trackId = id, startedAt = daysAgo(daysAgo), playedMs = 1, completed = true))

    private suspend fun ids(playlist: SmartPlaylist) = smart.observe(playlist).first().map { it.value }

    @Test
    fun `each playlist picks its own tracks`() =
        runTest {
            db.trackDao().upsert(
                listOf(
                    track("often", 200),
                    track("once", 200),
                    track("old-like", 300),
                    track("fresh", 3),
                    track("hidden", 2, hidden = true),
                ),
            )
            repeat(3) { listen("often", 5) }
            listen("once", 5)
            listen("old-like", 90)
            db.reactionDao().upsert(ReactionEntity("old-like", 1, 1))
            db.reactionDao().upsert(ReactionEntity("often", 1, 2))

            assertEquals(listOf("often"), ids(SmartPlaylist.OnRepeat))
            assertEquals(listOf("old-like"), ids(SmartPlaylist.Forgotten))
            assertEquals(listOf("fresh"), ids(SmartPlaylist.RecentlyAdded))
            assertEquals(listOf("fresh"), ids(SmartPlaylist.NeverPlayed))
        }
}
