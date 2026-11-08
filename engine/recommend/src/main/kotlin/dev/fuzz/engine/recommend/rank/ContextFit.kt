package dev.fuzz.engine.recommend.rank

import dev.fuzz.engine.recommend.config.ContextRange
import dev.fuzz.engine.recommend.model.WaveTrack
import kotlin.math.abs
import kotlin.math.min

/**
 * Насколько трек подходит под занятие или настроение, 0..1. Учитывает только то, что о треке известно:
 * без аудиопризнаков судим по тегам, без тегов и признаков ответ нейтральный [NEUTRAL].
 */
internal object ContextFit {
    const val NEUTRAL = 0.5f
    private const val BPM_TOLERANCE = 25f
    private const val UNIT_TOLERANCE = 0.25f
    private const val AVOID_PENALTY = 1f

    fun fit(
        track: WaveTrack,
        ranges: List<ContextRange>,
    ): Float {
        if (ranges.isEmpty()) return NEUTRAL
        return ranges.map { fitOne(track, it) }.average().toFloat()
    }

    private fun fitOne(
        track: WaveTrack,
        range: ContextRange,
    ): Float {
        val parts = ArrayList<Float>(PARTS)
        track.audio?.let { audio ->
            bpmFit(audio.bpm, range)?.let(parts::add)
            unitFit(audio.energy, range.energyMin, range.energyMax)?.let(parts::add)
            unitFit(audio.valence, range.valenceMin, range.valenceMax)?.let(parts::add)
        }
        tagFit(track, range)?.let(parts::add)
        val base = if (parts.isEmpty()) NEUTRAL else parts.average().toFloat()
        val avoided = range.avoidTags.maxOfOrNull { track.tags[it] ?: 0f } ?: 0f
        return (base - avoided * AVOID_PENALTY).coerceIn(0f, 1f)
    }

    /** Темп сравнивается с учётом половинного и двойного счёта: 70 BPM и 140 BPM — одна пульсация. */
    fun bpmFit(
        bpm: Float,
        range: ContextRange,
    ): Float? {
        val lo = range.bpmMin
        val hi = range.bpmMax
        if (lo == null && hi == null) return null
        val distance =
            listOf(bpm, bpm / 2, bpm * 2).minOf { distanceToRange(it, lo ?: 0f, hi ?: Float.MAX_VALUE) }
        return (1f - distance / BPM_TOLERANCE).coerceAtLeast(0f)
    }

    private fun unitFit(
        value: Float,
        lo: Float?,
        hi: Float?,
    ): Float? {
        if (lo == null && hi == null) return null
        val distance = distanceToRange(value, lo ?: 0f, hi ?: 1f)
        return (1f - distance / UNIT_TOLERANCE).coerceAtLeast(0f)
    }

    private fun tagFit(
        track: WaveTrack,
        range: ContextRange,
    ): Float? {
        if (range.tags.isEmpty() || track.tags.isEmpty()) return null
        val matched = range.tags.sumOf { (track.tags[it] ?: 0f).toDouble() }.toFloat()
        return min(1f, matched)
    }

    private fun distanceToRange(
        v: Float,
        lo: Float,
        hi: Float,
    ): Float =
        when {
            v < lo -> lo - v
            v > hi -> v - hi
            else -> 0f
        }.let(::abs)

    private const val PARTS = 4
}
