package dev.fuzz.core.data.online

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.fuzz.core.data.playlist.OfflinePlaylistRepository
import dev.fuzz.core.data.reaction.OfflineReactionRepository
import dev.fuzz.core.data.sync.Clock
import dev.fuzz.core.data.wave.ExternalTrackRegistry
import dev.fuzz.core.database.FuzzDatabase
import dev.fuzz.core.database.entity.DownloadEntity
import dev.fuzz.core.domain.connection.ConnectedSources
import dev.fuzz.core.domain.player.SavedStreams
import dev.fuzz.core.domain.plugin.ExternalId
import dev.fuzz.core.domain.plugin.LyricsProvider
import dev.fuzz.core.domain.plugin.MetadataProvider
import dev.fuzz.core.domain.plugin.MusicSource
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.SearchResult
import dev.fuzz.core.domain.plugin.SourceCapability
import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.core.model.Reaction
import dev.fuzz.core.model.TrackId
import dev.fuzz.engine.recommend.model.TrackOrigin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SavedOnlineTracksTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FuzzDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val clock =
        object : Clock() {
            override fun nowMs() = 42L
        }
    private val plugin = FakePlugin()
    private val connected = Connected(listOf(plugin))
    private val ref = TrackRef("Tum Hi Ho", "Arijit Singh", externalIds = listOf(ExternalId("saavn", "abc")))
    private val id = TrackId("online-1")

    private fun saved(registry: ExternalTrackRegistry) =
        SavedOnlineTracks(
            db.trackDao(),
            db.savedRefDao(),
            db.downloadDao(),
            db.wishDao(),
            registry,
            connected,
            emptyList(),
            clock,
        )

    @After
    fun tearDown() = db.close()

    @Test
    fun `like keeps a track from a plugin in the collection`() =
        runTest {
            val registry = ExternalTrackRegistry().apply { remember(id, plugin, ref, TrackOrigin.OpenCatalog) }
            val reactions = OfflineReactionRepository(db.reactionDao(), saved(registry))

            reactions.set(id, Reaction.Like)

            val row = db.trackDao().getByIds(listOf(id.value)).single()
            assertEquals("Tum Hi Ho", row.title)
            assertEquals("JioSaavn", row.format)
            assertEquals(SavedStreams.uriFor(id.value), row.playbackUri)
        }

    @Test
    fun `saved track plays after restart`() =
        runTest {
            val before = ExternalTrackRegistry().apply { remember(id, plugin, ref, TrackOrigin.OpenCatalog) }
            saved(before).persist(listOf(id))

            // Новый запуск: реестр пуст, источник берётся из подключений по сохранённому ключу.
            val uri = saved(ExternalTrackRegistry()).resolve(id.value)

            assertEquals("https://stream/abc", uri)
            assertEquals(listOf("abc"), plugin.asked)
        }

    @Test
    fun `playlist accepts a track from a plugin`() =
        runTest {
            val registry = ExternalTrackRegistry().apply { remember(id, plugin, ref, TrackOrigin.OpenCatalog) }
            val playlists = OfflinePlaylistRepository(db, db.playlistDao(), clock, saved(registry))
            val playlist = playlists.create("Болливуд")

            playlists.addTracks(playlist, listOf(id))

            assertEquals(listOf(id), playlists.observeTracks(playlist).first().map { it.id })
        }

    @Test
    fun `downloaded file plays without network`() =
        runTest {
            val before = ExternalTrackRegistry().apply { remember(id, plugin, ref, TrackOrigin.OpenCatalog) }
            saved(before).persist(listOf(id))
            val file = java.io.File.createTempFile("fuzz", ".m4a")
            db.downloadDao().upsert(DownloadEntity(id.value, file.path, 1, 1))

            val uri = saved(ExternalTrackRegistry()).resolve(id.value)

            assertEquals(android.net.Uri.fromFile(file).toString(), uri)
            assertEquals(emptyList<String>(), plugin.asked)
        }

    @Test
    fun `unknown track is not invented`() =
        runTest {
            saved(ExternalTrackRegistry()).persist(listOf(TrackId("local-file")))

            assertNull(db.trackDao().getAnyByIds(listOf("local-file")).firstOrNull())
        }

    private class FakePlugin : MusicSource {
        val asked = mutableListOf<String>()
        override val id = "js-saavn"
        override val displayName = "JioSaavn"
        override val capabilities = setOf(SourceCapability.STREAM)

        override suspend fun search(
            query: String,
            limit: Int,
        ): PluginResult<SearchResult> = PluginResult.Ok(SearchResult())

        override suspend fun playbackUri(track: TrackRef): PluginResult<String> {
            val external = track.externalIds.first().id
            asked += external
            return PluginResult.Ok("https://stream/$external")
        }
    }

    private class Connected(
        sources: List<MusicSource>,
    ) : ConnectedSources {
        override val sources = MutableStateFlow(sources)
        override val lyrics = MutableStateFlow(emptyList<LyricsProvider>())
        override val metadata = MutableStateFlow(emptyList<MetadataProvider>())
    }
}
