package dev.fuzz.engine.recommend

import dev.fuzz.core.model.Reaction
import dev.fuzz.core.model.TrackId
import dev.fuzz.core.model.wave.WaveSeed
import dev.fuzz.core.model.wave.WaveSettings
import dev.fuzz.engine.recommend.candidate.ArtistAnchor
import dev.fuzz.engine.recommend.candidate.CandidateGenerator
import dev.fuzz.engine.recommend.candidate.LibraryIndex
import dev.fuzz.engine.recommend.candidate.SeedInfo
import dev.fuzz.engine.recommend.candidate.SimilarityExplorer
import dev.fuzz.engine.recommend.candidate.TrackAnchor
import dev.fuzz.engine.recommend.candidate.WaveContext
import dev.fuzz.engine.recommend.config.WaveConfig
import dev.fuzz.engine.recommend.model.Anchor
import dev.fuzz.engine.recommend.model.Bucket
import dev.fuzz.engine.recommend.model.Candidate
import dev.fuzz.engine.recommend.model.Reason
import dev.fuzz.engine.recommend.model.ScoreTerm
import dev.fuzz.engine.recommend.model.WavePick
import dev.fuzz.engine.recommend.model.WaveSignal
import dev.fuzz.engine.recommend.model.WaveTrack
import dev.fuzz.engine.recommend.port.SimilarityGraph
import dev.fuzz.engine.recommend.port.WaveLibrary
import dev.fuzz.engine.recommend.profile.TasteProfile
import dev.fuzz.engine.recommend.profile.TasteProfileBuilder
import dev.fuzz.engine.recommend.rank.BatchAssembler
import dev.fuzz.engine.recommend.rank.BucketMix
import dev.fuzz.engine.recommend.rank.Score
import dev.fuzz.engine.recommend.rank.Scored
import dev.fuzz.engine.recommend.rank.Scorer
import dev.fuzz.engine.recommend.rank.WaveFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.random.Random

/**
 * Одна волна: пул кандидатов в памяти, профиль вкуса сессии и уже выданные треки.
 *
 * Жизненный цикл: [start] собирает первую пачку из локальных корзин (и того, что сетевые успели
 * за [dev.fuzz.engine.recommend.config.PoolConfig.startTimeoutMs]); сразу после старта вызывающий
 * запускает [enrich] в фоне, чтобы долить сетевые корзины. Очередь просит [nextBatch], когда впереди
 * остаётся 3 трека. На пропуск, лайк и дизлайк — [onSignal]: профиль обновляется, несыгранный
 * остаток пачки пересобирается из памяти без запросов к базе и сети.
 */
@Suppress("TooManyFunctions")
class WaveSession internal constructor(
    val seed: WaveSeed,
    settings: WaveSettings,
    profile: TasteProfile,
    private val library: WaveLibrary,
    private val graph: SimilarityGraph?,
    private val generators: List<CandidateGenerator>,
    private val config: WaveConfig,
    private val clock: () -> Long,
    private val randomSeed: Long,
) {
    private val mutex = Mutex()
    private val assembler = BatchAssembler(config.assembly)
    private val random = Random(randomSeed)

    var settings: WaveSettings = settings
        private set
    var profile: TasteProfile = profile
        private set

    private lateinit var index: LibraryIndex
    private lateinit var seedInfo: SeedInfo
    private var explorer: SimilarityExplorer? = null

    private val pool = LinkedHashMap<TrackId, Candidate>()
    private val issued = ArrayList<WavePick>()
    private val issuedAt = HashMap<TrackId, Int>()
    private val sessionDisliked = HashSet<TrackId>()
    private val bucketCounts = HashMap<Bucket, Int>()
    private val rounds = HashMap<CandidateGenerator, Int>()
    private val dry = HashSet<CandidateGenerator>()
    private val pendingRemote = HashSet<CandidateGenerator>()
    private var scorer: Scorer? = null
    private var started = false
    private var prepared = false
    private val candidateOf = HashMap<TrackId, Candidate>()

    /** Сколько треков сессия уже выдала. */
    val issuedCount: Int get() = issued.size

    /** Первая пачка. Повторный вызов отдаёт следующую. */
    suspend fun start(): List<WavePick> {
        val again =
            mutex.withLock {
                if (!prepared) prepare()
                started.also { started = true }
            }
        if (again) return nextBatch()
        fill(generators.filter { it.isLocal }, config.pool.generatorTimeoutMs)
        val slow = fill(generators.filterNot { it.isLocal }, config.pool.startTimeoutMs)
        return mutex.withLock {
            pendingRemote += slow
            val batch = ArrayList<WavePick>()
            seedTrackPick()?.let(batch::add)
            batch += assembleLocked(config.batchSize - batch.size)
            batch
        }
    }

    /** Долить сетевые корзины, не успевшие к старту. Безопасно звать параллельно с [nextBatch]. */
    suspend fun enrich() {
        val todo = mutex.withLock { pendingRemote.toList().also { pendingRemote.clear() } }
        fill(todo, config.pool.generatorTimeoutMs)
    }

    /** Следующая пачка из [WaveConfig.batchSize] треков. Сначала доливает пул, если он мелеет. */
    suspend fun nextBatch(): List<WavePick> {
        refillIfLow()
        return mutex.withLock { assembleLocked(config.batchSize) }
    }

    /**
     * Обратная связь. [upcoming] — уже выданные, но ещё не сыгранные треки очереди. Если сигнал
     * меняет вкус заметно (пропуск в первые 30 с, лайк, дизлайк), остаток пересобирается и
     * возвращается заново; иначе возвращается как был.
     */
    suspend fun onSignal(
        signal: WaveSignal,
        upcoming: List<TrackId>,
    ): List<WavePick> =
        mutex.withLock {
            profile =
                TasteProfileBuilder(config.profile, clock()) { index[it] ?: pool[it]?.track ?: issuedTrack(it) }
                    .startFrom(profile)
                    .add(signal, config.profile.sessionBoost)
                    .build()
            if (signal is WaveSignal.Dislike) {
                sessionDisliked += signal.trackId
                pool.remove(signal.trackId)
            }
            scorer = null
            if (reshapes(signal)) rebuildLocked(upcoming.filter { it != signal.trackId }) else picksFor(upcoming)
        }

    /** Смена настроек на ходу: пересчёт оценок и пересборка несыгранного остатка. */
    suspend fun updateSettings(
        newSettings: WaveSettings,
        upcoming: List<TrackId>,
    ): List<WavePick> =
        mutex.withLock {
            settings = newSettings
            scorer = null
            rebuildLocked(upcoming)
        }

    /** Объяснение для кнопки «Почему этот трек?». */
    suspend fun reason(trackId: TrackId): Reason? = mutex.withLock { issuedAt[trackId]?.let { issued[it].reason } }

    // --- внутреннее ---

    private suspend fun prepare() {
        index = LibraryIndex(library.tracks())
        seedInfo = SeedInfo.resolve(seed, index)
        explorer = graph?.let { SimilarityExplorer(it, artistAnchors(), trackAnchors()) }
        prepared = true
    }

    private fun reshapes(signal: WaveSignal): Boolean =
        when (signal) {
            is WaveSignal.Like, is WaveSignal.Dislike -> true
            is WaveSignal.Listen -> !signal.completed && signal.playedMs < config.profile.earlySkipMs
            else -> false
        }

    private fun rebuildLocked(upcoming: List<TrackId>): List<WavePick> {
        // Вернуть несыгранное в пул и выдать заново с учётом нового вкуса.
        val returned = upcoming.mapNotNull { id -> issuedAt[id]?.let { issued[it] } }
        returned.forEach { pick -> unissue(pick) }
        return assembleLocked(returned.size)
    }

    private fun unissue(pick: WavePick) {
        val at = issuedAt.remove(pick.track.id) ?: return
        issued.removeAt(at)
        for (i in at until issued.size) issuedAt[issued[i].track.id] = i
        bucketCounts.merge(pick.reason.bucket, -1, Int::plus)
        if (pick.track.id !in sessionDisliked) {
            pool.getOrPut(pick.track.id) { candidateOf[pick.track.id] ?: Candidate(pick.track, pick.reason.bucket) }
        }
    }

    private fun picksFor(ids: List<TrackId>): List<WavePick> = ids.mapNotNull { id -> issuedAt[id]?.let { issued[it] } }

    private fun issuedTrack(id: TrackId): WaveTrack? = issuedAt[id]?.let { issued[it].track }

    private fun assembleLocked(size: Int): List<WavePick> {
        if (size <= 0) return emptyList()
        val scorer = currentScorer()
        val filter = WaveFilter(settings)
        val fresh = eligible(filter, allowRepeats = false)
        val candidates = if (fresh.size >= size) fresh else fresh + eligible(filter, allowRepeats = true)
        val scored = candidates.map { Scored(it, scorer.score(it)) }
        val available = scored.map { it.bucket }.toSet()
        val shares = BucketMix.redistribute(baseShares(), available)
        val artistCap = if (seed is WaveSeed.Artist) config.batchSize else config.assembly.maxSameArtistPerBatch
        val tail = issued.takeLast(config.assembly.similarityWindow).map { it.track }
        return assembler
            .assemble(scored, size, shares, tail, bucketCounts, artistCap)
            .map { issue(it) }
    }

    private fun issue(s: Scored): WavePick {
        val pick = WavePick(s.track, s.score.total, reasonFor(s.candidate, s.score))
        issuedAt[s.track.id] = issued.size
        issued += pick
        candidateOf[s.track.id] = s.candidate
        pool.remove(s.track.id)
        return pick
    }

    /**
     * Подходящие кандидаты. Без повторов — всё, что ещё не выдавалось. С повторами (пул иссяк,
     * коллекция маленькая) — выданное давно: не ближе [dev.fuzz.engine.recommend.config.PoolConfig.minRepeatGap]
     * треков назад, а для крошечной коллекции — не ближе половины выданного.
     */
    private fun eligible(
        filter: WaveFilter,
        allowRepeats: Boolean,
    ): List<Candidate> {
        val ok = { c: Candidate ->
            !profile.isDisliked(c.track.id) && c.track.id !in sessionDisliked &&
                filter.accepts(c.track)
        }
        if (!allowRepeats) return pool.values.filter(ok)
        val gap = minOf(config.pool.minRepeatGap, maxOf(1, (issuedAt.size + pool.size) / 2))
        val cutoff = issued.size - gap
        return issued
            .subList(0, cutoff.coerceAtLeast(0))
            .asReversed()
            .distinctBy { it.track.id }
            .filter { issuedAt[it.track.id]?.let { at -> at < cutoff } == true }
            .map { candidateOf[it.track.id] ?: Candidate(it.track, it.reason.bucket) }
            .filter(ok)
    }

    private fun seedTrackPick(): WavePick? {
        val track = (seed as? WaveSeed.Track)?.let { index[it.trackId] } ?: return null
        val candidate = Candidate(track, Bucket.SIMILAR, 1f, Anchor(Anchor.Kind.SEED, track.title))
        return issue(Scored(candidate, currentScorer().score(candidate))).also {
            bucketCounts.merge(Bucket.SIMILAR, 1, Int::plus)
        }
    }

    private fun baseShares(): Map<Bucket, Float> =
        BucketMix.shares(
            config.mix,
            Scorer.effectiveFamiliarity(config, settings),
        )

    private fun currentScorer(): Scorer =
        scorer ?: Scorer(config, settings, profile, clock(), randomSeed, seedInfo.tags).also { scorer = it }

    private suspend fun refillIfLow() {
        val low =
            mutex.withLock {
                val filter = WaveFilter(settings)
                val counts = eligible(filter, allowRepeats = false).groupingBy { it.bucket }.eachCount()
                val need = config.batchSize * config.pool.refillFactor
                val shares = baseShares()
                generators.filter { g ->
                    g !in dry && (shares[g.bucket] ?: 0f) > 0f && (counts[g.bucket] ?: 0) < need
                }
            }
        if (low.isNotEmpty()) fill(low, config.pool.generatorTimeoutMs, nextRound = true)
    }

    /**
     * Опросить корзины параллельно и сложить ответы в пул. Запросы идут без блокировки, чтобы
     * пересборка после дизлайка не ждала сеть. Возвращает корзины, не успевшие за [timeoutMs].
     */
    private suspend fun fill(
        targets: List<CandidateGenerator>,
        timeoutMs: Long,
        nextRound: Boolean = false,
    ): List<CandidateGenerator> {
        if (targets.isEmpty()) return emptyList()
        val contexts =
            mutex.withLock {
                // Пул не исключаем: трек из «непрослушанного» может прийти и из «похожих» с объяснением.
                val exclude = issuedAt.keys + sessionDisliked
                val pooled = HashSet(pool.keys)
                targets.associateWith { g ->
                    val round = if (nextRound) (rounds[g] ?: 0) + 1 else rounds[g] ?: 0
                    rounds[g] = round
                    WaveContext(seed, settings, profile, index, seedInfo, round, exclude, pooled, explorer)
                }
            }
        val results =
            coroutineScope {
                contexts.map { (g, ctx) -> async { g to call(g, ctx, timeoutMs) } }.awaitAll()
            }
        return mutex.withLock {
            results.mapNotNull { (g, result) ->
                when (result) {
                    null -> {
                        g
                    }

                    else -> {
                        val added = merge(result)
                        if (added == 0 && nextRound) dry += g
                        null
                    }
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // сбой корзины только сужает волну
    private suspend fun call(
        g: CandidateGenerator,
        ctx: WaveContext,
        timeoutMs: Long,
    ): List<Candidate>? =
        try {
            withTimeout(timeoutMs) { g.candidates(ctx, config.pool.perGeneratorLimit) }
        } catch (e: TimeoutCancellationException) {
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }

    /** Сложить кандидатов в пул; возвращает, сколько треков добавилось впервые. */
    private fun merge(candidates: List<Candidate>): Int {
        var added = 0
        candidates.forEach { c ->
            val id = c.track.id
            if (id in issuedAt || id in sessionDisliked) return@forEach
            val prev = pool[id]
            if (prev == null) added++
            if (prev == null || replaces(prev, c)) pool[id] = c
        }
        return added
    }

    /**
     * Один трек может прийти из нескольких корзин. Корзина не меняется, чтобы не сбить пропорции
     * ползунка, кроме одного случая: непрослушанный трек, найденный ещё и графом похожести,
     * переезжает в «похожие» — так у него появляется объяснение «похож на …».
     */
    private fun replaces(
        prev: Candidate,
        next: Candidate,
    ): Boolean =
        if (prev.bucket == next.bucket) {
            next.strength > prev.strength
        } else {
            prev.bucket == Bucket.UNHEARD && next.bucket == Bucket.SIMILAR
        }

    private fun artistAnchors(): List<ArtistAnchor> {
        if (!seedInfo.isEmpty) {
            return seedInfo.artists.map { (_, name) -> ArtistAnchor(name, 1f, Anchor(Anchor.Kind.SEED, name)) }
        }
        val liked =
            profile.tracks
                .filterValues { it.reaction == Reaction.Like }
                .keys
                .mapNotNull { index[it]?.artistKey }
                .toSet()
        // Якоря — артисты, которых реально слушали и лайкали; вкус из одной коллекции — только запасной вариант.
        val behaved =
            profile.tracks.entries
                .filter { it.value.affinity > 0f }
                .mapNotNull { (id, s) -> index[id]?.let { it.artistKey to s.affinity } }
                .groupBy({ it.first }, { it.second })
                .mapValues { it.value.sum() }
                .entries
                .sortedByDescending { it.value }
                .take(ANCHOR_POOL)
                .map { it.key to it.value }
        val ranked = behaved.ifEmpty { profile.topArtists(ANCHOR_POOL) }
        val top = ranked.firstOrNull()?.second ?: return emptyList()
        // Лучшие артисты плюс случайная выборка из «середины» вкуса, чтобы каждая сессия звучала по-новому.
        val picked = ranked.take(TOP_ANCHORS) + ranked.drop(TOP_ANCHORS).shuffled(random).take(RANDOM_ANCHORS)
        return picked.map { (key, weight) ->
            val name = index.artistName(key) ?: key
            val kind = if (key in liked) Anchor.Kind.LIKED_ARTIST else Anchor.Kind.LISTENED_ARTIST
            ArtistAnchor(name, weight / top, Anchor(kind, name))
        }
    }

    private fun trackAnchors(): List<TrackAnchor> {
        if (seedInfo.tracks.isNotEmpty()) {
            return seedInfo.tracks.map { TrackAnchor(it, 1f, Anchor(Anchor.Kind.SEED, it.title)) }
        }
        val best =
            profile.tracks.entries
                .filter { it.value.affinity > 0f }
                .sortedByDescending { it.value.affinity }
                .take(TRACK_ANCHORS)
        val top = best.firstOrNull()?.value?.affinity ?: return emptyList()
        return best.mapNotNull { (id, stats) ->
            index[id]?.let { TrackAnchor(it, stats.affinity / top, Anchor(Anchor.Kind.LIKED_TRACK, it.title)) }
        }
    }

    private fun reasonFor(
        c: Candidate,
        score: Score,
    ): Reason {
        val term = score.topTerm
        val sharedTags =
            c.track.tags.keys
                .filter { (profile.tags[it] ?: 0f) > 0f || it in seedInfo.tags }
                .sortedByDescending { (profile.tags[it] ?: 0f) + (seedInfo.tags[it] ?: 0f) }
                .take(REASON_TAGS)
        return Reason(
            bucket = c.bucket,
            term = term,
            anchor = c.anchor,
            tags = sharedTags,
            bpm = if (term == ScoreTerm.CONTEXT_FIT) c.track.audio?.bpm else null,
        )
    }

    private companion object {
        const val ANCHOR_POOL = 80
        const val TOP_ANCHORS = 15
        const val RANDOM_ANCHORS = 10
        const val TRACK_ANCHORS = 40
        const val REASON_TAGS = 3
    }
}
