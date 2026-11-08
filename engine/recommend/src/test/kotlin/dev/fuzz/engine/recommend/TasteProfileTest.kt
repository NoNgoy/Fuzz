package dev.fuzz.engine.recommend

import dev.fuzz.core.model.Reaction
import dev.fuzz.core.model.TrackId
import dev.fuzz.engine.recommend.config.WaveConfig
import dev.fuzz.engine.recommend.model.WaveSignal
import dev.fuzz.engine.recommend.profile.TasteProfileBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TasteProfileTest {
    private val metal = track("m", "Deftones", mapOf("metal" to 1f), bpm = 140f)
    private val jazz = track("j", "Miles", mapOf("jazz" to 1f), bpm = 100f)
    private val byId = listOf(metal, jazz).associateBy { it.id }
    private val config = WaveConfig.Default.profile

    private fun builder(now: Long = NOW) = TasteProfileBuilder(config, now) { byId[it] }

    @Test
    fun `likes and completions build positive taste, early skips negative`() {
        val profile =
            builder()
                .add(WaveSignal.Like(metal.id, NOW))
                .add(WaveSignal.Listen(metal.id, NOW, 200_000, completed = true))
                .add(WaveSignal.Listen(jazz.id, NOW, 5_000, completed = false))
                .build()
        assertEquals(4f, profile.tags.getValue("metal"), 1e-4f)
        assertEquals(-1f, profile.tags.getValue("jazz"), 1e-4f)
        assertEquals(Reaction.Like, profile.stats(metal.id).reaction)
        assertEquals(1, profile.stats(jazz.id).earlySkips)
        assertTrue(profile.tagCosine(metal.tags) > 0.9f)
        assertEquals(140f, profile.audio!!.bpm, 1e-3f)
    }

    @Test
    fun `old events fade with half-life`() {
        val monthAgo = NOW - (config.halfLifeDays * 86_400_000L).toLong()
        val profile = builder().add(WaveSignal.Like(metal.id, monthAgo)).build()
        assertEquals(config.like / 2, profile.tags.getValue("metal"), 1e-3f)
    }

    @Test
    fun `clearing a like takes it back`() {
        val profile =
            builder()
                .add(WaveSignal.Like(metal.id, NOW))
                .add(WaveSignal.ClearReaction(metal.id, NOW))
                .build()
        assertEquals(0f, profile.tags.getValue("metal"), 1e-4f)
        assertEquals(Reaction.None, profile.stats(metal.id).reaction)
    }

    @Test
    fun `cold start uses the collection as a weak like`() {
        val profile = builder().addCollectionPrior(listOf(metal, jazz)).build()
        assertEquals(config.collectionPrior, profile.artists.getValue(metal.artistKey), 1e-4f)
        assertTrue(profile.tracks.isEmpty())
    }

    @Test
    fun `continuing from a snapshot keeps what was there`() {
        val base = builder().add(WaveSignal.Like(metal.id, NOW)).build()
        val next =
            builder()
                .startFrom(base)
                .add(WaveSignal.Dislike(jazz.id, NOW))
                .build()
        assertEquals(config.like, next.tags.getValue("metal"), 1e-4f)
        assertTrue(next.isDisliked(jazz.id))
        assertTrue(!next.isDisliked(TrackId("other")))
    }
}
