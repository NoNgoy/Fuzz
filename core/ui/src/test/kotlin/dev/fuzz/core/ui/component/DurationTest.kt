package dev.fuzz.core.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationTest {
    @Test
    fun `formats minutes and hours`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("3:07", formatDuration(187_000))
        assertEquals("1:02:15", formatDuration(3_735_000))
        assertEquals("0:00", formatDuration(-5))
    }
}
