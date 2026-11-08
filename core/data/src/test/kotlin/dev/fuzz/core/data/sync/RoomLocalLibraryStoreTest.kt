package dev.fuzz.core.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.fuzz.core.database.FuzzDatabase
import dev.fuzz.core.domain.library.ScannedTrack
import dev.fuzz.core.domain.library.SyncStats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomLocalLibraryStoreTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FuzzDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val clock =
        object : Clock() {
            var now = 1_000L

            override fun nowMs() = now
        }
    private val store = RoomLocalLibraryStore(db, db.trackDao(), clock)

    @After
    fun tearDown() = db.close()

    private fun track(
        n: Int,
        title: String = "Трек $n",
    ) = ScannedTrack(
        id = "local:external_primary:$n",
        title = title,
        artistName = "Кино",
        albumTitle = "Группа крови",
        durationMs = 200_000,
        playbackUri = "content://media/external_primary/audio/media/$n",
        artworkUri = null,
        trackNumber = n,
        year = 1988,
        format = "FLAC",
        addedAtMs = 500,
    )

    @Test
    fun `first scan adds everything`() =
        runTest {
            assertEquals(SyncStats(added = 2, updated = 0, removed = 0), store.replaceLocal(listOf(track(1), track(2))))
            assertEquals(2, db.trackDao().observeAll().first().size)
        }

    @Test
    fun `unchanged tracks are not rewritten`() =
        runTest {
            store.replaceLocal(listOf(track(1)))
            clock.now = 2_000

            assertEquals(SyncStats(0, 0, 0), store.replaceLocal(listOf(track(1))))
            assertEquals(1_000L, db.trackDao().getBySource("local").single().updatedAt)
        }

    @Test
    fun `changed tags update and missing files are hidden`() =
        runTest {
            store.replaceLocal(listOf(track(1), track(2)))

            val stats = store.replaceLocal(listOf(track(1, title = "Кукушка")))

            assertEquals(SyncStats(added = 0, updated = 1, removed = 1), stats)
            assertEquals(listOf("Кукушка"), db.trackDao().observeAll().first().map { it.title })
        }

    @Test
    fun `returning file is restored`() =
        runTest {
            store.replaceLocal(listOf(track(1)))
            store.replaceLocal(emptyList())

            assertEquals(SyncStats(added = 0, updated = 1, removed = 0), store.replaceLocal(listOf(track(1))))
            assertEquals(1, db.trackDao().observeAll().first().size)
        }
}
