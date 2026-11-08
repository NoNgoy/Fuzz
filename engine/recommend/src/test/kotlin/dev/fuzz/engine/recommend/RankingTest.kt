package dev.fuzz.engine.recommend

import dev.fuzz.core.model.wave.WaveActivity
import dev.fuzz.core.model.wave.WaveCharacter
import dev.fuzz.core.model.wave.WaveSettings
import dev.fuzz.engine.recommend.config.WaveConfig
import dev.fuzz.engine.recommend.model.Bucket
import dev.fuzz.engine.recommend.model.Candidate
import dev.fuzz.engine.recommend.model.ScoreTerm
import dev.fuzz.engine.recommend.model.WaveSignal
import dev.fuzz.engine.recommend.profile.TasteProfile
import dev.fuzz.engine.recommend.profile.TasteProfileBuilder
import dev.fuzz.engine.recommend.rank.BatchAssembler
import dev.fuzz.engine.recommend.rank.BucketMix
import dev.fuzz.engine.recommend.rank.Score
import dev.fuzz.engine.recommend.rank.Scored
import dev.fuzz.engine.recommend.rank.Scorer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RankingTest {
    private val config = WaveConfig.Default

    @Test
    fun `default config loads from resources`() {
        assertEquals(1, config.version)
        assertEquals(10, config.batchSize)
        assertEquals(WaveActivity.entries.toSet(), config.activities.keys)
    }

    @Test
    fun `slider moves shares from favorites to new buckets`() {
        val left = BucketMix.shares(config.mix, 0)
        val mid = BucketMix.shares(config.mix, 60)
        val right = BucketMix.shares(config.mix, 100)
        assertEquals(1f, mid.values.sum(), 1e-4f)
        assertTrue(left.getValue(Bucket.FAVORITES) > mid.getValue(Bucket.FAVORITES))
        assertTrue(mid.getValue(Bucket.FAVORITES) > right.getValue(Bucket.FAVORITES))
        assertTrue(right.getValue(Bucket.PREVIEW) > mid.getValue(Bucket.PREVIEW))
    }

    @Test
    fun `missing buckets hand their share to the rest`() {
        val shares = BucketMix.redistribute(BucketMix.shares(config.mix, 50), setOf(Bucket.FAVORITES, Bucket.UNHEARD))
        assertEquals(setOf(Bucket.FAVORITES, Bucket.UNHEARD), shares.keys)
        assertEquals(1f, shares.values.sum(), 1e-4f)
    }

    @Test
    fun `activity prefers fitting tempo and energy`() {
        val calm = track("c", "A", mapOf("ambient" to 1f), bpm = 65f, energy = 0.1f)
        val loud = track("l", "B", mapOf("metal" to 1f), bpm = 150f, energy = 0.95f)
        val scorer = scorer(WaveSettings(activity = WaveActivity.FallingAsleep))
        val calmScore = scorer.score(Candidate(calm, Bucket.UNHEARD))
        val loudScore = scorer.score(Candidate(loud, Bucket.UNHEARD))
        assertTrue(calmScore.terms.getValue(ScoreTerm.CONTEXT_FIT) > loudScore.terms.getValue(ScoreTerm.CONTEXT_FIT))
        assertTrue(calmScore.total > loudScore.total)
    }

    @Test
    fun `slider flips the sign of novelty`() {
        val heard = track("h", "A")
        val profile =
            TasteProfileBuilder(config.profile, NOW) { heard.takeIf { h -> h.id == it } }
                .add(WaveSignal.Listen(heard.id, NOW - 30L * 86_400_000, 200_000, completed = true))
                .build()
        val familiar = scorer(WaveSettings(familiarity = 0), profile).score(Candidate(heard, Bucket.FAVORITES))
        val fresh = scorer(WaveSettings(familiarity = 100), profile).score(Candidate(heard, Bucket.FAVORITES))
        assertTrue(familiar.terms.getValue(ScoreTerm.NOVELTY) > 0f)
        assertTrue(fresh.terms.getValue(ScoreTerm.NOVELTY) < 0f)
    }

    @Test
    fun `character preset overrides the slider`() {
        assertEquals(
            90,
            Scorer.effectiveFamiliarity(config, WaveSettings(familiarity = 10, character = WaveCharacter.Unfamiliar)),
        )
        assertEquals(30, Scorer.effectiveFamiliarity(config, WaveSettings(familiarity = 30)))
    }

    @Test
    fun `batch never puts three tracks of one artist in a row`() {
        val pool =
            (0 until 10).map { scored(track("x$it", "Solo"), 10f) } +
                (0 until 10).map { scored(track("y$it", "Other $it"), 1f) }
        val batch =
            BatchAssembler(
                config.assembly,
            ).assemble(pool, 10, mapOf(Bucket.UNHEARD to 1f), emptyList(), HashMap(), artistCap = 10)
        assertEquals(10, batch.size)
        batch.windowed(3).forEach { w -> assertTrue(w.map { it.track.artistKey }.toSet().size > 1) }
    }

    @Test
    fun `batch keeps going when only one artist is left`() {
        val pool = (0 until 5).map { scored(track("x$it", "Solo"), 1f) }
        val batch =
            BatchAssembler(
                config.assembly,
            ).assemble(pool, 5, mapOf(Bucket.UNHEARD to 1f), emptyList(), HashMap())
        assertEquals(5, batch.size)
    }

    @Test
    fun `batch follows bucket shares`() {
        val pool =
            (0 until 20).map { scored(track("f$it", "F$it"), 5f, Bucket.FAVORITES) } +
                (0 until 20).map { scored(track("u$it", "U$it"), 1f, Bucket.UNHEARD) }
        val counts = HashMap<Bucket, Int>()
        BatchAssembler(
            config.assembly,
        ).assemble(pool, 10, mapOf(Bucket.FAVORITES to 0.3f, Bucket.UNHEARD to 0.7f), emptyList(), counts)
        assertEquals(3, counts[Bucket.FAVORITES])
        assertEquals(7, counts[Bucket.UNHEARD])
    }

    @Test
    fun `smooth transitions are preferred`() {
        val assembler = BatchAssembler(config.assembly)
        val prev = track("p", "P", bpm = 120f, energy = 0.5f)
        assertEquals(0f, assembler.jump(prev, track("n", "N", bpm = 122f, energy = 0.55f)))
        assertTrue(assembler.jump(prev, track("n2", "N", bpm = 175f, energy = 1f)) > 0.5f)
        // 70 и 140 BPM — одна пульсация
        assertEquals(0f, assembler.jump(track("a", "A", bpm = 70f), track("b", "B", bpm = 140f)))
    }

    private fun scorer(
        settings: WaveSettings,
        profile: TasteProfile = TasteProfile.EMPTY,
    ) = Scorer(config, settings, profile, NOW, jitterSeed = 1)

    private fun scored(
        t: dev.fuzz.engine.recommend.model.WaveTrack,
        total: Float,
        bucket: Bucket = Bucket.UNHEARD,
    ) = Scored(Candidate(t, bucket), Score(total, emptyMap()))
}
