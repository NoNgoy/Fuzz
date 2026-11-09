package dev.fuzz.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatHoursTest {
    @Test
    fun `small numbers keep one decimal and big ones are whole`() {
        assertEquals("0", formatHours(0))
        assertEquals("1,5", formatHours(5_400_000))
        assertEquals("2", formatHours(7_200_000))
        assertEquals("12", formatHours(45_000_000))
    }
}
