package dev.fuzz.core.data.wave

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.fuzz.core.database.FuzzDatabase
import dev.fuzz.core.domain.plugin.AlbumRef
import dev.fuzz.core.domain.plugin.ArtistRef
import dev.fuzz.core.domain.plugin.MetadataProvider
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.Similar
import dev.fuzz.core.domain.plugin.Tag
import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.engine.recommend.model.TrackHint
import dev.fuzz.engine.recommend.model.Weighted
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Провайдер, который можно «отключить от сети». */
private class SwitchableMeta : MetadataProvider {
    override val id = "m"
    override val displayName = "m"
    var online = true
    var calls = 0

    override suspend fun similarArtists(artist: ArtistRef, limit: Int): PluginResult<List<Similar<ArtistRef>>> {
        calls++
        return if (online) {
            PluginResult.Ok(listOf(Similar(ArtistRef("Tool"), 0.7f), Similar(ArtistRef("Deftones"), 0.3f)))
        } else {
            PluginResult.Failure(PluginResult.Kind.NETWORK)
        }
    }

    override suspend fun similarTracks(track: TrackRef, limit: Int): PluginResult<List<Similar<TrackRef>>> {
        calls++
        return if (online) {
            PluginResult.Ok(listOf(Similar(TrackRef("Aerials", "System\tof a Down"), 0.9f)))
        } else {
            PluginResult.Failure(PluginResult.Kind.NETWORK)
        }
    }

    override suspend fun tags(track: TrackRef) = PluginResult.Ok(emptyList<Tag>())

    override suspend fun discography(artist: ArtistRef) = PluginResult.Ok(emptyList<AlbumRef>())

    override suspend fun coverUrl(album: AlbumRef): PluginResult<String> =
        PluginResult.Failure(PluginResult.Kind.NOT_FOUND)
}

@RunWith(RobolectricTestRunner::class)
class PersistentSimilarityTest {
    private val db =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FuzzDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private var clock = 1_000L
    private val store = RoomSimilarityStore(db.similarityCacheDao()) { clock }
    private val meta = SwitchableMeta()

    /** Новый граф = перезапуск приложения: кэш в памяти пуст, в Room остаётся. */
    private fun graph() =
        ProviderSimilarityGraph(
            listOf(meta to 1f),
            store = store,
            storeTtlMs = DAY,
            maxAgeMs = 30 * DAY,
            now = { clock },
        )

    @After
    fun close() = db.close()

    @Test
    fun answersSurviveRestartWithoutNetworkCall() =
        runBlocking {
            assertEquals(listOf("Tool", "Deftones"), graph().similarArtists("SOAD", 5).map { it.item })
            meta.online = false
            val again = graph().similarArtists("soad ", 5)
            assertEquals(listOf("Tool", "Deftones"), again.map { it.item })
            assertEquals(0.7f, again[0].weight, 0.001f)
            assertEquals(1, meta.calls)
        }

    @Test
    fun staleEntryRefetchesButServesOfflineFallback() =
        runBlocking {
            graph().similarTracks(TrackHint("SOAD", "Toxicity"), 5)
            clock += 2 * DAY
            meta.online = false
            val offline = graph().similarTracks(TrackHint("SOAD", "Toxicity"), 5)
            assertEquals(2, meta.calls)
            // Табуляция в имени не ломает формат хранения.
            assertEquals(TrackHint("System of a Down", "Aerials"), offline.single().item)
        }

    @Test
    fun oldEntriesArePrunedOnFirstWrite() =
        runBlocking {
            store.putArtists("old", listOf(Weighted("X", 1f)))
            clock += 31 * DAY
            graph().similarArtists("new", 5)
            assertNull(store.artists("old"))
            assertTrue(store.artists("new") != null)
        }

    @Test
    fun corruptPayloadLinesAreSkipped() =
        runBlocking {
            db.similarityCacheDao().put(
                dev.fuzz.core.database.entity.SimilarityCacheEntity("artist", "k", "0.5\tTool\nbroken\n\n", 1),
            )
            assertEquals(listOf(Weighted("Tool", 0.5f)), store.artists("k")!!.value)
        }

    @Test
    fun extraProvidersAreReadOnEveryNetworkCall() =
        runBlocking {
            var extra = emptyList<Pair<MetadataProvider, Float>>()
            val g = ProviderSimilarityGraph(emptyList(), extraProviders = { extra })
            assertTrue(g.similarArtists("A", 5).isEmpty())
            extra = listOf(meta to 0.5f)
            val result = g.similarArtists("B", 5)
            assertEquals("Tool", result.first().item)
            assertEquals(0.35f, result.first().weight, 0.001f)
        }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
