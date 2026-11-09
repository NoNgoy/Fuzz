package dev.fuzz.engine.recommend

import dev.fuzz.core.model.TrackId
import dev.fuzz.core.model.wave.WaveSeed
import dev.fuzz.core.model.wave.WaveSettings
import dev.fuzz.engine.recommend.model.Anchor
import dev.fuzz.engine.recommend.model.Bucket
import dev.fuzz.engine.recommend.model.TrackOrigin
import dev.fuzz.engine.recommend.model.WaveSignal
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WaveSessionTest {
    private val collection = smallCollection()

    private fun engine(
        graph: FakeGraph? = null,
        previews: FakePreviews? = null,
    ) = WaveEngine(FakeLibrary(collection), graph, listOfNotNull(previews), clock = { NOW })

    private fun likedMetalProfile(engine: WaveEngine) =
        engine.buildProfile(
            collection,
            collection.filter { "metal" in it.tags }.take(6).map { WaveSignal.Like(it.id, NOW - 86_400_000) },
        )

    @Test
    fun `first batch has ten tracks and every next batch is new`() =
        runBlocking {
            val engine = engine()
            val session = engine.session(WaveSeed.MyWave, WaveSettings(), likedMetalProfile(engine), randomSeed = 1)
            val first = session.start()
            assertEquals(10, first.size)
            val ids = first.map { it.track.id }.toMutableSet()
            repeat(5) { session.nextBatch().forEach { assertTrue(ids.add(it.track.id), "повтор ${it.track.id}") } }
        }

    @Test
    fun `wave never ends even on a tiny collection`() =
        runBlocking {
            val tiny = collection.take(12)
            val engine = WaveEngine(FakeLibrary(tiny), clock = { NOW })
            val session =
                engine.session(
                    WaveSeed.MyWave,
                    WaveSettings(),
                    engine.buildProfile(tiny, emptyList()),
                    randomSeed = 1,
                )
            val batches = listOf(session.start()) + (0 until 20).map { session.nextBatch() }
            assertTrue(batches.all { it.isNotEmpty() }, "волна оборвалась")
            val all = batches.flatten()
            // Повтор не раньше, чем через половину коллекции.
            all.map { it.track.id }.windowed(6).forEach { assertEquals(6, it.toSet().size) }
        }

    @Test
    fun `dislike removes the track and reshapes the rest`() =
        runBlocking {
            val engine = engine()
            val session = engine.session(WaveSeed.MyWave, WaveSettings(), likedMetalProfile(engine), randomSeed = 1)
            val batch = session.start()
            val current = batch.first()
            val upcoming = batch.drop(1).map { it.track.id }
            val rebuilt = session.onSignal(WaveSignal.Dislike(current.track.id, NOW), upcoming)
            assertEquals(upcoming.size, rebuilt.size)
            assertTrue(rebuilt.none { it.track.id == current.track.id })
            val more = (0 until 10).flatMap { session.nextBatch() }
            assertTrue(more.none { it.track.id == current.track.id })
        }

    @Test
    fun `completed listen keeps the queue as is`() =
        runBlocking {
            val engine = engine()
            val session = engine.session(WaveSeed.MyWave, WaveSettings(), likedMetalProfile(engine), randomSeed = 1)
            val batch = session.start()
            val upcoming = batch.drop(1).map { it.track.id }
            val same = session.onSignal(WaveSignal.Listen(batch.first().track.id, NOW, 200_000, true), upcoming)
            assertEquals(upcoming, same.map { it.track.id })
        }

    @Test
    fun `similar artists come with an explanation`() =
        runBlocking {
            val graph =
                FakeGraph(mapOf("Artist 0" to listOf("Artist 4" to 0.9f, "Artist 8" to 0.8f, "Outside" to 0.7f)))
            val previews =
                FakePreviews(
                    mapOf(
                        "Outside" to listOf(track("o1", "Outside", mapOf("metal" to 1f), origin = TrackOrigin.Preview)),
                    ),
                )
            val engine = engine(graph, previews)
            val session =
                engine.session(
                    WaveSeed.MyWave,
                    WaveSettings(familiarity = 90),
                    likedMetalProfile(engine),
                    randomSeed = 1,
                )
            val first = session.start()
            session.enrich()
            val picks = first + (0 until 6).flatMap { session.nextBatch() }
            val similar = picks.filter { it.reason.bucket == Bucket.SIMILAR }
            assertTrue(similar.isNotEmpty())
            assertTrue(
                similar.any {
                    it.reason.anchor?.kind == Anchor.Kind.LIKED_ARTIST &&
                        it.reason.anchor.name == "Artist 0"
                },
            )
            val preview = picks.firstOrNull { it.track.id == TrackId("o1") }
            assertNotNull(preview, "превью вне коллекции не попало в волну")
            assertNotNull(session.reason(preview!!.track.id))
        }

    @Test
    fun `wave takes favourite artists from own servers even without similarity`() =
        runBlocking {
            val server =
                FakeServer(
                    mapOf(
                        "Artist 0" to
                            (1..4).map {
                                track("s$it", "Artist 0", mapOf("metal" to 1f), origin = TrackOrigin.OpenCatalog)
                            },
                    ),
                )
            val engine = WaveEngine(FakeLibrary(collection), FakeGraph(emptyMap()), listOf(server), clock = { NOW })
            val session =
                engine.session(
                    WaveSeed.MyWave,
                    WaveSettings(familiarity = 30),
                    likedMetalProfile(engine),
                    randomSeed = 1,
                )
            val picks = session.start() + (0 until 4).flatMap { session.nextBatch() }
            assertTrue(picks.any { it.track.id.value.startsWith("s") }, "треки с сервера не попали в волну")
        }

    @Test
    fun `failing network only narrows the wave`() =
        runBlocking {
            val engine = engine(FakeGraph(emptyMap(), failing = true))
            val session = engine.session(WaveSeed.MyWave, WaveSettings(), likedMetalProfile(engine), randomSeed = 1)
            assertEquals(10, session.start().size)
            assertEquals(10, session.nextBatch().size)
        }

    @Test
    fun `slow network does not delay the start`() =
        runBlocking {
            val slow =
                object : dev.fuzz.engine.recommend.port.SimilarityGraph {
                    override suspend fun similarArtists(
                        artist: String,
                        limit: Int,
                    ) = delay(10_000).let { emptyList<dev.fuzz.engine.recommend.model.Weighted<String>>() }

                    override suspend fun similarTracks(
                        track: dev.fuzz.engine.recommend.model.TrackHint,
                        limit: Int,
                    ) = emptyList<dev.fuzz.engine.recommend.model.Weighted<dev.fuzz.engine.recommend.model.TrackHint>>()
                }
            val engine = WaveEngine(FakeLibrary(collection), slow, clock = { NOW })
            val session = engine.session(WaveSeed.MyWave, WaveSettings(), likedMetalProfile(engine), randomSeed = 1)
            val t0 = System.currentTimeMillis()
            assertEquals(10, session.start().size)
            assertTrue(System.currentTimeMillis() - t0 < 1000)
        }

    @Test
    fun `track wave starts with the track and stays close`() =
        runBlocking {
            val seed = collection.first { "jazz" in it.tags }
            val engine = engine()
            val session =
                engine.session(
                    WaveSeed.Track(seed.id),
                    WaveSettings(),
                    likedMetalProfile(engine),
                    randomSeed = 1,
                )
            val batch = session.start()
            assertEquals(seed.id, batch.first().track.id)
            val jazz = batch.count { "jazz" in it.track.tags }
            assertTrue(jazz >= 7, "джаза в волне по джазовому треку $jazz из 10")
        }

    @Test
    fun `genre exclusion is strict`() =
        runBlocking {
            val engine = engine()
            val session =
                engine.session(
                    WaveSeed.MyWave,
                    WaveSettings(excludedGenres = setOf("metal")),
                    likedMetalProfile(engine),
                    randomSeed = 1,
                )
            val picks = session.start() + session.nextBatch()
            assertFalse(picks.any { "metal" in it.track.tags })
        }

    @Test
    fun `settings change rebuilds the upcoming tracks`() =
        runBlocking {
            val engine = engine()
            val session = engine.session(WaveSeed.MyWave, WaveSettings(), likedMetalProfile(engine), randomSeed = 1)
            val batch = session.start()
            val rebuilt = session.updateSettings(WaveSettings(genres = setOf("ambient")), batch.map { it.track.id })
            assertEquals(batch.size, rebuilt.size)
            assertTrue(rebuilt.all { "ambient" in it.track.tags })
        }
}
