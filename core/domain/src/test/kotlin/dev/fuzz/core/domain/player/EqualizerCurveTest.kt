package dev.fuzz.core.domain.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EqualizerCurveTest {
    @Test
    fun `reference bands get preset gains as is`() {
        assertEquals(EqualizerPreset.Rock.gainsDb, EqualizerPreset.Rock.gainsFor(EqualizerPreset.REFERENCE_HZ))
    }

    @Test
    fun `other bands are interpolated on log scale and clamped at edges`() {
        // 117 Гц — середина между 60 и 230 в логарифмической шкале.
        val gains = EqualizerPreset.Bass.gainsFor(listOf(31, 117, 16_000))
        assertEquals(6f, gains[0], 0.01f)
        assertEquals(5f, gains[1], 0.05f)
        assertEquals(0f, gains[2], 0.01f)
    }

    @Test
    fun `custom gains are clamped to the phone range`() {
        val bands = EqualizerBands(listOf(60, 230, 910), minDb = -3f, maxDb = 3f, hasBassBoost = false)
        val settings = SoundSettings(preset = null, customGainsDb = listOf(10f, -10f))
        assertEquals(listOf(3f, -3f, 0f), settings.gainsFor(bands))
    }
}
