package dev.fuzz.core.player.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FadeTest {
    @Test
    fun `crossfade starts fade seconds before the end`() {
        assertFalse(crossfadeDue(190_000, 200_000, 5_000))
        assertTrue(crossfadeDue(195_000, 200_000, 5_000))
        assertTrue("заранее готовим хвост", crossfadeDue(190_000, 200_000, 5_000, leadMs = 5_000))
    }

    @Test
    fun `off, unknown duration or a too short track never overlap`() {
        assertFalse(crossfadeDue(199_000, 200_000, 0))
        assertFalse(crossfadeDue(0, Long.MIN_VALUE + 1, 5_000))
        assertFalse(crossfadeDue(9_000, 10_000, 6_000))
    }

    @Test
    fun `equal power curve`() {
        assertEquals(1f to 0f, crossfadeVolumes(0f))
        val (out, into) = crossfadeVolumes(0.5f)
        assertEquals(out, into, 0.001f)
        assertEquals(1f, out * out + into * into, 0.001f)
        assertEquals(0f, crossfadeVolumes(1f).first, 0.001f)
    }
}
