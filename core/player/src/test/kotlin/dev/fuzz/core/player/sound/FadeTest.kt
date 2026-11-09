package dev.fuzz.core.player.sound

import org.junit.Assert.assertEquals
import org.junit.Test

class FadeTest {
    @Test
    fun `volume rises at start and falls at end`() {
        assertEquals(0f, fadeVolume(0, 200_000, 5_000), 0.001f)
        assertEquals(0.5f, fadeVolume(2_500, 200_000, 5_000), 0.001f)
        assertEquals(1f, fadeVolume(100_000, 200_000, 5_000), 0.001f)
        assertEquals(0.2f, fadeVolume(199_000, 200_000, 5_000), 0.001f)
    }

    @Test
    fun `off or unknown duration keeps full volume`() {
        assertEquals(1f, fadeVolume(0, 200_000, 0), 0.001f)
        assertEquals(1f, fadeVolume(0, Long.MIN_VALUE + 1, 5_000), 0.001f)
    }

    @Test
    fun `short track fades over half of its length at most`() {
        assertEquals(1f, fadeVolume(4_000, 8_000, 12_000), 0.001f)
    }
}
