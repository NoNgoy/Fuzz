package dev.fuzz.sim.wave

import dev.fuzz.core.model.TrackId
import dev.fuzz.core.model.wave.WaveSeed
import dev.fuzz.core.model.wave.WaveSettings
import dev.fuzz.engine.recommend.WaveEngine
import dev.fuzz.engine.recommend.WaveSession
import dev.fuzz.engine.recommend.model.WavePick
import dev.fuzz.engine.recommend.model.WaveSignal
import dev.fuzz.engine.recommend.profile.TasteProfile
import kotlin.random.Random

/**
 * Синтетический слушатель: любит одни кластеры, терпит другие, не выносит третьи.
 * Решает, дослушать, пропустить (и на какой секунде), лайкнуть или дизлайкнуть.
 */
class SimListener(
    val loved: Set<Int>,
    val hated: Set<Int>,
    seed: Long = 7,
) {
    private val random = Random(seed)

    sealed interface Verdict {
        data class Complete(val like: Boolean) : Verdict

        data class Skip(val atMs: Long, val dislike: Boolean) : Verdict
    }

    fun judge(cluster: Int): Verdict =
        when {
            cluster in loved -> {
                if (random.nextFloat() <
                    LOVED_SKIP
                ) {
                    Verdict.Skip(LATE_SKIP_MS, false)
                } else {
                    Verdict.Complete(random.nextFloat() < LIKE_RATE)
                }
            }

            cluster in hated -> {
                Verdict.Skip(EARLY_SKIP_MS, random.nextFloat() < DISLIKE_RATE)
            }

            random.nextFloat() < NEUTRAL_COMPLETE -> {
                Verdict.Complete(false)
            }

            else -> {
                Verdict.Skip(EARLY_SKIP_MS * 2, false)
            }
        }

    private companion object {
        const val LOVED_SKIP = 0.1f
        const val LIKE_RATE = 0.15f
        const val DISLIKE_RATE = 0.4f
        const val NEUTRAL_COMPLETE = 0.5f
        const val EARLY_SKIP_MS = 10_000L
        const val LATE_SKIP_MS = 90_000L
    }
}

/** Что произошло за прогон: сыгранное по порядку и замеры времени. */
class SimulationResult(
    val played: List<WavePick>,
    val startMs: Long,
    val rebuildMs: List<Long>,
    val listenedMs: Long,
    val heardBefore: Set<TrackId>,
)

/**
 * Прогон волны: очередь как у QueueManager (следующая пачка, когда впереди 3 трека), сигналы после
 * каждого трека, пересборка остатка после пропуска, лайка и дизлайка.
 */
class WaveSimulation(
    private val world: SyntheticWorld,
    private val listener: SimListener,
    private val online: Boolean = true,
    private val nowMs: Long = NOW,
) {
    val engine =
        WaveEngine(
            library = world.library,
            graph = world.graph.takeIf { online },
            catalogs = if (online) listOf(world.previews, world.jamendo) else emptyList(),
            clock = { nowMs },
        )

    /** История «до установки»: пара месяцев прослушиваний коллекции тем же слушателем. */
    fun priorHistory(events: Int = PRIOR_EVENTS): List<WaveSignal> {
        val random = Random(HISTORY_SEED)
        val loved = world.collection.filter { world.clusterOf(it.id) in listener.loved }
        val other = world.collection.filter { world.clusterOf(it.id) !in listener.loved }
        return (0 until events).flatMap { i ->
            val at = nowMs - random.nextLong(HOUR_MS, HISTORY_SPAN_MS)
            val track = if (random.nextFloat() < LOVED_SHARE) loved.random(random) else other.random(random)
            val cluster = world.clusterOf(track.id)
            val listen =
                when {
                    cluster in listener.loved -> WaveSignal.Listen(track.id, at, track.durationMs, completed = true)
                    cluster in listener.hated -> WaveSignal.Listen(track.id, at, EARLY_SKIP_MS, completed = false)
                    random.nextBoolean() -> WaveSignal.Listen(track.id, at, track.durationMs, completed = true)
                    else -> WaveSignal.Listen(track.id, at, EARLY_SKIP_MS * 2, completed = false)
                }
            if (i % LIKE_EVERY == 0 &&
                world.clusterOf(track.id) in listener.loved
            ) {
                listOf(listen, WaveSignal.Like(track.id, at + 1))
            } else {
                listOf(listen)
            }
        }
    }

    fun profile(history: List<WaveSignal>): TasteProfile = engine.buildProfile(world.collection, history, nowMs)

    suspend fun run(
        tracks: Int,
        settings: WaveSettings = WaveSettings(),
        seed: WaveSeed = WaveSeed.MyWave,
        history: List<WaveSignal> = priorHistory(),
    ): SimulationResult {
        val profile = profile(history)
        val session = engine.session(seed, settings, profile, randomSeed = SESSION_SEED)
        val t0 = System.nanoTime()
        val queue = ArrayDeque(session.start())
        val startMs = (System.nanoTime() - t0) / NANOS_PER_MS
        session.enrich()
        val played = ArrayList<WavePick>(tracks)
        val rebuilds = ArrayList<Long>()
        var clock = nowMs
        var listened = 0L
        while (played.size < tracks) {
            if (queue.size <= REFILL_AT) queue.addAll(session.nextBatch())
            val pick = queue.removeFirstOrNull() ?: break
            played += pick
            val verdict = listener.judge(world.clusterOf(pick.track.id))
            val signals = signalsFor(pick, verdict, clock)
            val playedMs = (signals.first() as WaveSignal.Listen).playedMs
            listened += playedMs
            clock += playedMs
            for (signal in signals) {
                val upcoming = queue.map { it.track.id }
                val r0 = System.nanoTime()
                val rebuilt = session.onSignal(signal, upcoming)
                if (signal !is WaveSignal.Listen ||
                    !signal.completed
                ) {
                    rebuilds += (System.nanoTime() - r0) / NANOS_PER_MS
                }
                queue.clear()
                queue.addAll(rebuilt)
            }
        }
        return SimulationResult(played, startMs, rebuilds, listened, history.map { it.trackId }.toSet())
    }

    private fun signalsFor(
        pick: WavePick,
        verdict: SimListener.Verdict,
        at: Long,
    ): List<WaveSignal> =
        when (verdict) {
            is SimListener.Verdict.Complete -> {
                listOfNotNull(
                    WaveSignal.Listen(pick.track.id, at, pick.track.durationMs, completed = true),
                    if (verdict.like) WaveSignal.Like(pick.track.id, at) else null,
                )
            }

            is SimListener.Verdict.Skip -> {
                listOfNotNull(
                    WaveSignal.Listen(pick.track.id, at, verdict.atMs, completed = false),
                    if (verdict.dislike) WaveSignal.Dislike(pick.track.id, at) else null,
                )
            }
        }

    companion object {
        const val NOW = 1_800_000_000_000L
        const val SESSION_SEED = 2026L
        private const val PRIOR_EVENTS = 300
        private const val HISTORY_SEED = 11L
        private const val LOVED_SHARE = 0.7f
        private const val LIKE_EVERY = 5
        private const val HOUR_MS = 3_600_000L
        private const val HISTORY_SPAN_MS = 60 * 24 * HOUR_MS
        private const val REFILL_AT = 3
        private const val EARLY_SKIP_MS = 10_000L
        private const val NANOS_PER_MS = 1_000_000L
    }
}

/** Проверка сессии напрямую, без слушателя: нужна тестам на пересчёт. */
suspend fun WaveSession.drain(n: Int): List<WavePick> {
    val out = ArrayList<WavePick>(n)
    out += start()
    while (out.size < n) {
        val next = nextBatch()
        if (next.isEmpty()) break
        out += next
    }
    return out
}
