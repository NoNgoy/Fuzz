package dev.fuzz.engine.recommend.rank

import dev.fuzz.core.model.wave.WaveSettings
import dev.fuzz.engine.recommend.config.ContextRange
import dev.fuzz.engine.recommend.config.WaveConfig
import dev.fuzz.engine.recommend.model.AudioFeatures
import dev.fuzz.engine.recommend.model.Candidate
import dev.fuzz.engine.recommend.model.ScoreTerm
import dev.fuzz.engine.recommend.profile.TasteProfile
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.tanh

/** Оценка кандидата и вклад каждого слагаемого (нужен для «Почему этот трек?»). */
data class Score(
    val total: Float,
    val terms: Map<ScoreTerm, Float>,
) {
    /** Слагаемое с наибольшим положительным вкладом, без случайной добавки. */
    val topTerm: ScoreTerm
        get() =
            terms.entries
                .filter { it.key != ScoreTerm.JITTER }
                .maxByOrNull { it.value }
                ?.takeIf { it.value > 0f }
                ?.key ?: ScoreTerm.SOURCE
}

/**
 * Формула из архитектуры:
 * score = w_t·cos(теги, вкус) + w_ar·артист + w_a·sim(аудио) + w_c·fit(занятие, настроение)
 *       + w_n·новизна + w_p·популярность + w_s·уверенность корзины − w_r·недавно − w_sk·пропуски + шум.
 *
 * Знак новизны зависит от ползунка: слева знакомое получает бонус, справа — новое.
 */
class Scorer(
    private val config: WaveConfig,
    private val settings: WaveSettings,
    private val profile: TasteProfile,
    private val nowMs: Long,
    /** Зерно сессии: шум стабилен для трека внутри сессии и разный между сессиями. */
    private val jitterSeed: Long,
    /** Теги и артисты, от которых стартовала волна по треку, артисту, жанру или плейлисту. */
    private val seedTags: Map<String, Float> = emptyMap(),
) {
    private val w = config.weights
    private val familiarity = effectiveFamiliarity(config, settings) / MAX_SLIDER
    private val noveltyWeight = w.novelty * (2f * familiarity - 1f)
    private val popularityWeight =
        settings.character?.let { config.characters[it]?.popularityWeight } ?: w.popularity
    private val contexts: List<ContextRange> =
        listOfNotNull(
            settings.activity?.let { config.activities[it] },
            settings.mood?.let { config.moods[it] },
        )
    private val seedNorm = TasteProfile.norm(seedTags)
    private val recentHalfLifeMs = config.profile.recentHalfLifeHours * MS_PER_HOUR
    private val maxArtist = profile.artists.values.maxOrNull()?.coerceAtLeast(1f) ?: 1f

    fun score(candidate: Candidate): Score {
        val track = candidate.track
        val stats = profile.stats(track.id)
        val terms = HashMap<ScoreTerm, Float>(TERMS)
        val tagCos =
            if (seedTags.isEmpty()) {
                profile.tagCosine(track.tags)
            } else {
                SEED_SHARE * TasteProfile.cosine(track.tags, seedTags, seedNorm) +
                    (1 - SEED_SHARE) * profile.tagCosine(track.tags)
            }
        terms[ScoreTerm.TASTE_TAGS] = w.tasteTags * tagCos
        terms[ScoreTerm.ARTIST] = w.artist * tanh((profile.artists[track.artistKey] ?: 0f) / maxArtist * ARTIST_SCALE)
        terms[ScoreTerm.AUDIO] = w.audio * audioSimilarity(track.audio, profile.audio)
        if (contexts.isNotEmpty()) {
            terms[ScoreTerm.CONTEXT_FIT] = w.contextFit * (ContextFit.fit(track, contexts) - ContextFit.NEUTRAL) * 2f
        }
        terms[ScoreTerm.NOVELTY] = noveltyWeight * novelty(stats.plays)
        terms[ScoreTerm.POPULARITY] = popularityWeight * ((track.popularity ?: POPULARITY_UNKNOWN) - POPULARITY_UNKNOWN)
        terms[ScoreTerm.SOURCE] = w.source * candidate.strength.coerceIn(0f, 1f)
        terms[ScoreTerm.RECENT] = -w.recent * recency(stats.lastPlayedAtMs)
        terms[ScoreTerm.SKIPS] = -w.skips * skipRate(stats.plays, stats.skips, stats.earlySkips)
        terms[ScoreTerm.JITTER] = w.jitter * jitter(track.id.value)
        return Score(terms.values.sum(), terms)
    }

    private fun jitter(id: String): Float {
        var h = id.hashCode().toLong() xor jitterSeed
        h = (h xor (h ushr MIX_SHIFT_1)) * MIX_MULT_1
        h = (h xor (h ushr MIX_SHIFT_2)) * MIX_MULT_2
        h = h xor (h ushr MIX_SHIFT_1)
        return (h ushr MIX_SHIFT_3).toFloat() / MIX_RANGE
    }

    private fun novelty(plays: Int): Float = if (plays == 0) 1f else -(1f - 1f / (1 + plays))

    private fun recency(lastPlayedAtMs: Long?): Float {
        if (lastPlayedAtMs == null) return 0f
        val age = (nowMs - lastPlayedAtMs).coerceAtLeast(0).toFloat()
        return 0.5f.pow(age / recentHalfLifeMs)
    }

    private fun skipRate(
        plays: Int,
        skips: Int,
        earlySkips: Int,
    ): Float {
        if (plays == 0) return 0f
        return ((earlySkips + LATE_SKIP_SHARE * (skips - earlySkips)) / (plays + 1)).coerceIn(0f, 1f)
    }

    private fun audioSimilarity(
        a: AudioFeatures?,
        taste: AudioFeatures?,
    ): Float {
        if (a == null || taste == null) return 0f
        val bpm = exp(-abs(a.bpm - taste.bpm) / BPM_SCALE)
        val energy = 1f - abs(a.energy - taste.energy)
        val valence = 1f - abs(a.valence - taste.valence)
        return (bpm + energy + valence) / AUDIO_PARTS - AUDIO_CENTER
    }

    companion object {
        private const val MAX_SLIDER = 100f
        private const val SEED_SHARE = 0.65f
        private const val ARTIST_SCALE = 2f
        private const val POPULARITY_UNKNOWN = 0.3f
        private const val LATE_SKIP_SHARE = 0.3f
        private const val BPM_SCALE = 30f
        private const val AUDIO_PARTS = 3f
        private const val AUDIO_CENTER = 0.5f
        private const val MS_PER_HOUR = 3_600_000f
        private const val TERMS = 10
        private const val MIX_SHIFT_1 = 33
        private const val MIX_SHIFT_2 = 29
        private const val MIX_SHIFT_3 = 40
        private const val MIX_MULT_1 = -0xae502812aa7333L
        private const val MIX_MULT_2 = -0x3b314601e57a13adL
        private const val MIX_RANGE = (1L shl 24).toFloat()

        /** Характер — быстрый пресет поверх ползунка (раздел 4.1 ТЗ). */
        fun effectiveFamiliarity(
            config: WaveConfig,
            settings: WaveSettings,
        ): Int = settings.character?.let { config.characters[it]?.familiarity } ?: settings.familiarity
    }
}
