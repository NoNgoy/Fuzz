package dev.fuzz.core.common.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextNormalizerTest {
    @Test
    fun `lowercases, strips marks and punctuation`() {
        assertEquals("beyonce deja vu", TextNormalizer.normalize("Beyoncé — Déjà Vu!"))
        assertEquals("ezh", TextNormalizer.normalize("Ёж"))
    }

    @Test
    fun `transliterates cyrillic`() {
        assertEquals("kino gruppa krovi", TextNormalizer.normalize("Кино — Группа крови"))
    }

    @Test
    fun `phonetic key matches transliterated english`() {
        val query = TextNormalizer.phonetic("систем оф даун")
        val name = TextNormalizer.phonetic("System of a Down")
        assertTrue(name.contains(query) || query.contains(name), "$query vs $name")
    }
}
