package dev.fuzz.core.data.wave

import dev.fuzz.core.domain.plugin.AlbumRef
import dev.fuzz.core.domain.plugin.ArtistRef
import dev.fuzz.core.domain.plugin.ExternalId
import dev.fuzz.core.domain.plugin.MetadataProvider
import dev.fuzz.core.domain.plugin.MusicSource
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.SearchResult
import dev.fuzz.core.domain.plugin.Similar
import dev.fuzz.core.domain.plugin.SourceCapability
import dev.fuzz.core.domain.plugin.Tag
import dev.fuzz.core.domain.plugin.TagSearchable
import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.engine.recommend.model.TrackHint
import dev.fuzz.engine.recommend.model.TrackOrigin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeMeta(
    override val id: String,
    private val artists: PluginResult<List<Similar<ArtistRef>>>,
    private val tracks: PluginResult<List<Similar<TrackRef>>> = PluginResult.Ok(emptyList()),
) : MetadataProvider {
    override val displayName = id
    var calls = 0

    override suspend fun similarArtists(artist: ArtistRef, limit: Int): PluginResult<List<Similar<ArtistRef>>> {
        calls++
        return artists
    }

    override suspend fun similarTracks(track: TrackRef, limit: Int) = tracks

    override suspend fun tags(track: TrackRef) = PluginResult.Ok(emptyList<Tag>())

    override suspend fun discography(artist: ArtistRef) = PluginResult.Ok(emptyList<AlbumRef>())

    override suspend fun coverUrl(album: AlbumRef): PluginResult<String> =
        PluginResult.Failure(PluginResult.Kind.NOT_FOUND)
}

private class FakeSource(private val found: List<TrackRef>) : MusicSource, TagSearchable {
    override val id = "fake"
    override val displayName = "fake"
    override val capabilities = setOf(SourceCapability.STREAM)
    var lastTags: List<String>? = null

    override suspend fun search(query: String, limit: Int) = PluginResult.Ok(SearchResult(tracks = found))

    override suspend fun playbackUri(track: TrackRef): PluginResult<String> =
        PluginResult.Failure(PluginResult.Kind.NOT_FOUND)

    override suspend fun byTags(tags: List<String>, limit: Int, offset: Int): PluginResult<SearchResult> {
        lastTags = tags
        return PluginResult.Ok(SearchResult(tracks = found))
    }
}

class WaveAdaptersTest {
    private fun artist(name: String) = ArtistRef(name)

    @Test
    fun graphSumsProvidersCapsAtOneAndSkipsSelf() =
        runBlocking {
            val a = FakeMeta("a", PluginResult.Ok(listOf(Similar(artist("Tool"), 0.7f), Similar(artist("SOAD"), 1f))))
            val b =
                FakeMeta(
                    "b",
                    PluginResult.Ok(listOf(Similar(artist("tool"), 0.6f), Similar(artist("Deftones"), 0.2f))),
                )
            val graph = ProviderSimilarityGraph(listOf(a to 1f, b to 1f))
            val result = graph.similarArtists("SOAD", 10)
            assertEquals(listOf("Tool", "Deftones"), result.map { it.item })
            assertEquals(1f, result[0].weight, 0.001f)
        }

    @Test
    fun graphSurvivesProviderFailureAndCaches() =
        runBlocking {
            val bad = FakeMeta("bad", PluginResult.Failure(PluginResult.Kind.NETWORK))
            val good = FakeMeta("good", PluginResult.Ok(listOf(Similar(artist("Tool"), 0.5f))))
            val graph = ProviderSimilarityGraph(listOf(bad to 1f, good to 1f))
            assertEquals(1, graph.similarArtists("X", 5).size)
            graph.similarArtists("X", 5)
            assertEquals(1, good.calls)
        }

    @Test
    fun graphReturnsEmptyWhenEverythingFails() =
        runBlocking {
            val bad = FakeMeta("bad", PluginResult.Failure(PluginResult.Kind.TIMEOUT))
            assertTrue(ProviderSimilarityGraph(listOf(bad to 1f)).similarArtists("X", 5).isEmpty())
        }

    @Test
    fun similarTracksMapToHints() =
        runBlocking {
            val similar = PluginResult.Ok(listOf(Similar(TrackRef("T", "A"), 0.4f)))
            val m = FakeMeta("m", PluginResult.Ok(emptyList()), similar)
            val result = ProviderSimilarityGraph(listOf(m to 0.5f)).similarTracks(TrackHint("X", "Y"), 5)
            assertEquals(TrackHint("A", "T"), result.single().item)
            assertEquals(0.2f, result.single().weight, 0.001f)
        }

    private fun ref(id: String, title: String = "Song", artist: String = "Band") =
        TrackRef(title, artist, externalIds = listOf(ExternalId("jamendo", id)))

    @Test
    fun catalogMapsTracksWithStableIdsAndDropsUnknown() =
        runBlocking {
            val source = FakeSource(listOf(ref("1"), TrackRef("NoId", "Band")))
            val catalog = SourceExternalCatalog(source, TrackOrigin.OpenCatalog, "jamendo")
            val first = catalog.byArtist("Band", 5)
            assertEquals(1, first.size)
            assertEquals(first.single().id, catalog.byArtist("Band", 5).single().id)
            assertEquals(TrackOrigin.OpenCatalog, first.single().origin)
        }

    @Test
    fun catalogByTagsPassesTagsAndMarksThem() =
        runBlocking {
            val source = FakeSource(listOf(ref("2")))
            val catalog = SourceExternalCatalog(source, TrackOrigin.OpenCatalog, "jamendo")
            val tracks = catalog.byTags(listOf("rock", "energetic"), 10, 0)
            assertEquals(listOf("rock", "energetic"), source.lastTags)
            assertEquals(setOf("rock", "energetic"), tracks.single().tags.keys)
        }

    @Test
    fun catalogFindMatchesByPhoneticKeys() =
        runBlocking {
            val source = FakeSource(listOf(ref("3", "Chop Suey!", "System of a Down")))
            val catalog = SourceExternalCatalog(source, TrackOrigin.Preview, "jamendo")
            assertNotNull(catalog.find(TrackHint("System Of A Down", "Chop Suey")))
            assertNull(catalog.find(TrackHint("Other", "Other")))
        }

    @Test
    fun ttlCacheExpires() {
        var t = 0L
        val cache = TtlCache<String, String>(ttlMs = 10, now = { t })
        cache.put("k", "v")
        assertEquals("v", cache.get("k"))
        t = 11
        assertNull(cache.get("k"))
    }
}
