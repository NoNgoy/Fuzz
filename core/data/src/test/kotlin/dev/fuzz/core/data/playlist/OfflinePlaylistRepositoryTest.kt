package dev.fuzz.core.data.playlist

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.fuzz.core.data.online.SavedOnlineTracks
import dev.fuzz.core.data.sync.Clock
import dev.fuzz.core.data.wave.ExternalTrackRegistry
import dev.fuzz.core.database.FuzzDatabase
import dev.fuzz.core.database.entity.TrackEntity
import dev.fuzz.core.domain.connection.ConnectedSources
import dev.fuzz.core.domain.plugin.LyricsProvider
import dev.fuzz.core.domain.plugin.MetadataProvider
import dev.fuzz.core.domain.plugin.MusicSource
import dev.fuzz.core.model.TrackId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OfflinePlaylistRepositoryTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FuzzDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val clock =
        object : Clock() {
            var now = 1L

            override fun nowMs() = now++
        }
    private val repo =
        OfflinePlaylistRepository(
            db,
            db.playlistDao(),
            clock,
            SavedOnlineTracks(
                db.trackDao(),
                db.savedRefDao(),
                db.downloadDao(),
                ExternalTrackRegistry(),
                NoConnections,
                emptyList(),
                clock,
            ),
        )

    private object NoConnections : ConnectedSources {
        override val sources = MutableStateFlow(emptyList<MusicSource>())
        override val lyrics = MutableStateFlow(emptyList<LyricsProvider>())
        override val metadata = MutableStateFlow(emptyList<MetadataProvider>())
    }

    private fun track(n: Int) =
        TrackEntity(
            id = "t$n",
            title = "Трек $n",
            artistName = "Кино",
            albumTitle = null,
            durationMs = 1,
            playbackUri = "content://$n",
            artworkUri = null,
            trackNumber = null,
            year = null,
            format = null,
            addedAt = 0,
            sourceId = "local",
            updatedAt = 0,
        )

    @Before
    fun setUp() = runTest { db.trackDao().upsert((1..4).map(::track)) }

    @After
    fun tearDown() = db.close()

    private suspend fun titles(id: dev.fuzz.core.model.PlaylistId) = repo.observeTracks(id).first().map { it.id.value }

    @Test
    fun `add skips duplicates and keeps order`() =
        runTest {
            val id = repo.create("  Дорога  ")
            assertEquals(2, repo.addTracks(id, listOf(TrackId("t2"), TrackId("t1"), TrackId("t2"))))
            assertEquals(1, repo.addTracks(id, listOf(TrackId("t1"), TrackId("t3"))))

            assertEquals(listOf("t2", "t1", "t3"), titles(id))
            val playlist = repo.observePlaylist(id).first()!!
            assertEquals("Дорога", playlist.name)
            assertEquals(3, playlist.trackCount)
        }

    @Test
    fun `move and remove keep positions dense`() =
        runTest {
            val id = repo.create("Спортзал")
            repo.addTracks(id, listOf("t1", "t2", "t3", "t4").map(::TrackId))

            repo.moveTrack(id, from = 3, to = 0)
            repo.removeTrack(id, TrackId("t2"))

            assertEquals(listOf("t4", "t1", "t3"), titles(id))
            assertEquals(listOf(0, 1, 2), db.playlistDao().entries(id.value).map { it.position })
        }

    @Test
    fun `hidden tracks are not shown or counted`() =
        runTest {
            val id = repo.create("Работа")
            repo.addTracks(id, listOf("t1", "t2").map(::TrackId))
            db.trackDao().markDeleted(listOf("t1"), now = 5)

            assertEquals(listOf("t2"), titles(id))
            assertEquals(1, repo.observePlaylists().first().single().trackCount)
        }

    @Test
    fun `deleted playlist disappears and fresh ones go first`() =
        runTest {
            val old = repo.create("Старый")
            val fresh = repo.create("Новый")
            assertEquals(listOf("Новый", "Старый"), repo.observePlaylists().first().map { it.name })

            repo.delete(fresh)

            assertNull(repo.observePlaylist(fresh).first())
            assertEquals(listOf(old), repo.observePlaylists().first().map { it.id })
        }
}
