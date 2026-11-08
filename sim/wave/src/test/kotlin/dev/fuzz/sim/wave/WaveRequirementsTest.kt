package dev.fuzz.sim.wave

import dev.fuzz.core.model.Reaction
import dev.fuzz.core.model.wave.WaveActivity
import dev.fuzz.core.model.wave.WaveSeed
import dev.fuzz.core.model.wave.WaveSettings
import dev.fuzz.engine.recommend.model.Bucket
import dev.fuzz.engine.recommend.model.TrackOrigin
import dev.fuzz.engine.recommend.model.WavePick
import dev.fuzz.engine.recommend.model.WaveSignal
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Требования раздела 4.3 ТЗ на синтетическом мире: 600 треков в коллекции, сотни артистов вокруг. */
class WaveRequirementsTest {
    private val world = SyntheticWorld()
    private val listener = SimListener(loved = setOf(0, 1, 10), hated = setOf(8, 4))

    @Test
    fun `no repeats for two hours with slider in the middle`() =
        runBlocking {
            val result = WaveSimulation(world, listener).run(tracks = 120)
            val twoHours = prefixByTime(result.played, TWO_HOURS_MS)
            assertTrue(twoHours.size >= MIN_TRACKS_IN_TWO_HOURS, "за 2 часа сыграно всего ${twoHours.size}")
            assertEquals(twoHours.size, twoHours.map { it.track.id }.toSet().size, "повторы за 2 часа")
        }

    @Test
    fun `no repeats for two hours offline either`() =
        runBlocking {
            val result = WaveSimulation(world, listener, online = false).run(tracks = 120)
            val twoHours = prefixByTime(result.played, TWO_HOURS_MS)
            assertEquals(twoHours.size, twoHours.map { it.track.id }.toSet().size, "повторы за 2 часа без сети")
            assertTrue(result.played.all { it.track.origin == TrackOrigin.Collection })
        }

    @Test
    fun `slider above 70 gives at least half never heard tracks`() =
        runBlocking {
            for (slider in listOf(75, 90, 100)) {
                val result =
                    WaveSimulation(
                        world,
                        listener,
                    ).run(tracks = 200, settings = WaveSettings(familiarity = slider))
                val newShare = result.played.count { it.track.id !in result.heardBefore } / result.played.size.toFloat()
                assertTrue(newShare >= 0.5f, "ползунок $slider: новых только ${(newShare * 100).toInt()}%")
            }
        }

    @Test
    fun `slider at familiar end is mostly known tracks`() =
        runBlocking {
            val result = WaveSimulation(world, listener).run(tracks = 100, settings = WaveSettings(familiarity = 0))
            val known = result.played.count { it.track.id in result.heardBefore } / result.played.size.toFloat()
            assertTrue(known >= 0.5f, "при ползунке 0 знакомых только ${(known * 100).toInt()}%")
        }

    @Test
    fun `start under one second and rebuild after dislike under 300 ms`() =
        runBlocking {
            WaveSimulation(world, listener).run(tracks = 30) // прогрев JIT, как на живом телефоне после старта
            val result = WaveSimulation(world, listener).run(tracks = 300)
            assertTrue(result.startMs < START_BUDGET_MS, "старт ${result.startMs} мс")
            val worst = result.rebuildMs.maxOrNull() ?: 0
            assertTrue(worst < REBUILD_BUDGET_MS, "пересчёт до $worst мс")
        }

    @Test
    fun `flow is rich and infinite, not five new tracks`() =
        runBlocking {
            val result = WaveSimulation(world, listener).run(tracks = 1000, settings = WaveSettings(familiarity = 60))
            assertEquals(1000, result.played.size, "поток оборвался")
            val distinct = result.played.map { it.track.id }.toSet()
            val newDistinct = distinct.count { it !in result.heardBefore }
            val artists = result.played.map { it.track.artistKey }.toSet()
            assertTrue(distinct.size >= 700, "уникальных треков ${distinct.size}")
            assertTrue(newDistinct >= 400, "новых уникальных треков $newDistinct")
            assertTrue(artists.size >= 100, "артистов ${artists.size}")
            val outside = result.played.count { it.track.origin != TrackOrigin.Collection }
            assertTrue(outside >= 100, "вне коллекции всего $outside")
        }

    @Test
    fun `hard rules hold on a long run`() =
        runBlocking {
            val result = WaveSimulation(world, listener).run(tracks = 1000)
            result.played.windowed(3).forEach { w ->
                assertTrue(w.map { it.track.artistKey }.toSet().size > 1, "три трека ${w[0].track.artist} подряд")
            }
            val disliked = HashSet<String>()
            // Дизлайкнутое в сессии больше не появляется: проверяем по повторному прогону сигналов.
            val sim = WaveSimulation(world, listener)
            val profile = sim.profile(sim.priorHistory())
            val session = sim.engine.session(WaveSeed.MyWave, WaveSettings(), profile, randomSeed = 1)
            val first = session.start()
            first.take(3).forEach {
                disliked += it.track.id.value
                session.onSignal(WaveSignal.Dislike(it.track.id, WaveSimulation.NOW), emptyList())
            }
            val later = (0 until 50).flatMap { session.nextBatch() }
            assertTrue(later.none { it.track.id.value in disliked }, "дизлайкнутый трек вернулся")
            assertTrue(profile.tracks.values.none { it.reaction == Reaction.Dislike })
        }

    @Test
    fun `wave adapts towards what the listener loves`() =
        runBlocking {
            val result = WaveSimulation(world, listener).run(tracks = 400)

            fun share(part: List<WavePick>, clusters: Set<Int>) =
                part.count { world.clusterOf(it.track.id) in clusters } / part.size.toFloat()
            val late = result.played.takeLast(200)
            assertTrue(share(late, listener.loved) > share(late, listener.hated) * 3, "любимое не перевешивает")
            assertTrue(share(late, listener.hated) < 0.1f, "нелюбимого ${share(late, listener.hated)}")
        }

    @Test
    fun `falling asleep is calmer than workout`() =
        runBlocking {
            fun energy(picks: List<WavePick>) = picks.mapNotNull { it.track.audio?.energy }.average()
            val sleep =
                WaveSimulation(
                    world,
                    listener,
                ).run(tracks = 60, settings = WaveSettings(activity = WaveActivity.FallingAsleep))
            val gym =
                WaveSimulation(
                    world,
                    listener,
                ).run(tracks = 60, settings = WaveSettings(activity = WaveActivity.Workout))
            assertTrue(
                energy(sleep.played) + 0.25 < energy(gym.played),
                "сон ${energy(sleep.played)}, спорт ${energy(gym.played)}",
            )
        }

    @Test
    fun `artist wave stays around the artist`() =
        runBlocking {
            val artist = world.artists.first { it.inCollection && it.cluster == 2 }
            val result = WaveSimulation(world, listener).run(tracks = 50, seed = WaveSeed.Artist(artist.name))
            val near = result.played.count { world.clusterOf(it.track.id).let { c -> c == 2 || c == 1 || c == 3 } }
            assertTrue(near >= 40, "рядом с артистом только $near из 50")
            assertTrue(result.played.take(10).any { it.track.artistKey == artist.tracks.first().artistKey })
        }

    @Test
    fun `every pick explains itself`() =
        runBlocking {
            val result = WaveSimulation(world, listener).run(tracks = 100)
            val similar =
                result.played.filter {
                    it.reason.bucket == Bucket.SIMILAR ||
                        it.reason.bucket == Bucket.PREVIEW
                }
            assertTrue(similar.isNotEmpty())
            assertTrue(similar.all { it.reason.anchor != null }, "у похожего нет якоря «похож на …»")
        }

    private fun prefixByTime(
        picks: List<WavePick>,
        budgetMs: Long,
    ): List<WavePick> {
        var total = 0L
        return picks.takeWhile {
            total += it.track.durationMs
            total <= budgetMs
        }
    }

    private companion object {
        const val TWO_HOURS_MS = 2 * 3_600_000L
        const val MIN_TRACKS_IN_TWO_HOURS = 25
        const val START_BUDGET_MS = 1000L
        const val REBUILD_BUDGET_MS = 300L
    }
}
