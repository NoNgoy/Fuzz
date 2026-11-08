package dev.fuzz.engine.recommend.rank

import dev.fuzz.engine.recommend.config.AssemblyConfig
import dev.fuzz.engine.recommend.model.Bucket
import dev.fuzz.engine.recommend.model.Candidate
import dev.fuzz.engine.recommend.model.WaveTrack
import dev.fuzz.engine.recommend.profile.TasteProfile
import kotlin.math.abs

/** Кандидат пула с посчитанной оценкой. */
data class Scored(
    val candidate: Candidate,
    val score: Score,
) {
    val track: WaveTrack get() = candidate.track
    val bucket: Bucket get() = candidate.bucket
}

/**
 * Жадная сборка пачки (раздел 4.2 ТЗ, пункт «Поток»).
 *
 * На каждое место выбирается корзина с наибольшим отставанием от своей доли (доли копятся за всю
 * сессию, поэтому пропорции держатся и на коротких пачках), внутри неё — кандидат с лучшим MMR:
 * оценка минус похожесть на последние выбранные треки минус резкий скачок темпа и энергии.
 * Жёстко: не больше [AssemblyConfig.maxSameArtistInRow] треков одного артиста подряд. Если иначе
 * собрать нельзя (в коллекции один артист), правило ослабляется, чтобы поток не обрывался.
 */
class BatchAssembler(
    private val config: AssemblyConfig,
) {
    /**
     * @param pool подходящие кандидаты (без дизлайков и уже сыгранного)
     * @param tail последние треки сессии, к которым пачка должна плавно примкнуть
     * @param bucketCounts сколько треков каждой корзины уже выдано за сессию; обновляется
     * @param artistCap сколько треков одного артиста можно в пачке
     */
    fun assemble(
        pool: List<Scored>,
        size: Int,
        shares: Map<Bucket, Float>,
        tail: List<WaveTrack>,
        bucketCounts: MutableMap<Bucket, Int>,
        artistCap: Int = config.maxSameArtistPerBatch,
    ): List<Scored> {
        val byBucket = pool.groupBy { it.bucket }.mapValues { (_, list) -> list.toMutableList() }
        val picked = ArrayList<Scored>(size)
        val recent = ArrayList(tail)
        val perArtist = HashMap<String, Int>()
        repeat(size) {
            val pick = pickNext(byBucket, shares, recent, bucketCounts, perArtist, artistCap) ?: return picked
            byBucket[pick.bucket]?.remove(pick)
            picked += pick
            recent += pick.track
            perArtist.merge(pick.track.artistKey, 1, Int::plus)
            bucketCounts.merge(pick.bucket, 1, Int::plus)
        }
        return picked
    }

    private fun pickNext(
        byBucket: Map<Bucket, List<Scored>>,
        shares: Map<Bucket, Float>,
        recent: List<WaveTrack>,
        counts: Map<Bucket, Int>,
        perArtist: Map<String, Int>,
        artistCap: Int,
    ): Scored? {
        val total = counts.values.sum() + 1
        val order =
            byBucket.keys
                .filter { byBucket[it].orEmpty().isNotEmpty() }
                .sortedByDescending { (shares[it] ?: 0f) * total - (counts[it] ?: 0) }
        for (strict in listOf(true, false)) {
            for (bucket in order) {
                if (strict && (shares[bucket] ?: 0f) <= 0f) continue
                best(byBucket.getValue(bucket), recent, perArtist, artistCap, strict)?.let { return it }
            }
        }
        return null
    }

    private fun best(
        candidates: List<Scored>,
        recent: List<WaveTrack>,
        perArtist: Map<String, Int>,
        artistCap: Int,
        strict: Boolean,
    ): Scored? {
        val window = recent.takeLast(config.similarityWindow)
        val previous = recent.lastOrNull()
        return candidates
            .asSequence()
            .filter { !strict || allowed(it.track, recent, perArtist, artistCap) }
            .maxByOrNull { mmr(it, window, previous) }
    }

    private fun allowed(
        track: WaveTrack,
        recent: List<WaveTrack>,
        perArtist: Map<String, Int>,
        artistCap: Int,
    ): Boolean {
        val run = recent.takeLast(config.maxSameArtistInRow)
        val tooManyInRow = run.size == config.maxSameArtistInRow && run.all { it.artistKey == track.artistKey }
        return !tooManyInRow && (perArtist[track.artistKey] ?: 0) < artistCap
    }

    private fun mmr(
        s: Scored,
        window: List<WaveTrack>,
        previous: WaveTrack?,
    ): Float {
        val redundancy = window.maxOfOrNull { similarity(s.track, it) } ?: 0f
        return s.score.total - config.mmrLambda * redundancy - config.transitionWeight * jump(previous, s.track)
    }

    /** Похожесть двух треков для MMR: тот же артист почти дубль, иначе косинус тегов. */
    internal fun similarity(
        a: WaveTrack,
        b: WaveTrack,
    ): Float {
        val tags = TasteProfile.cosine(a.tags, b.tags).coerceAtLeast(0f)
        return if (a.artistKey == b.artistKey) SAME_ARTIST + (1 - SAME_ARTIST) * tags else tags * TAG_SHARE
    }

    /** Скачок темпа и энергии между соседними треками, 0 — плавно. */
    internal fun jump(
        previous: WaveTrack?,
        next: WaveTrack,
    ): Float {
        val a = previous?.audio ?: return 0f
        val b = next.audio ?: return 0f
        val bpm = listOf(b.bpm, b.bpm / 2, b.bpm * 2).minOf { abs(it - a.bpm) } / config.bpmJumpScale
        val energy = abs(a.energy - b.energy) / config.energyJumpScale
        return ((bpm + energy) / 2f - JUMP_TOLERANCE).coerceAtLeast(0f)
    }

    private companion object {
        const val SAME_ARTIST = 0.7f
        const val TAG_SHARE = 0.6f
        const val JUMP_TOLERANCE = 0.2f
    }
}
