package dev.fuzz.core.common.text

import java.text.Normalizer

/**
 * Приводит строки к виду для поиска: нижний регистр, без диакритики и пунктуации, «ё» как «е»,
 * кириллица транслитерирована в латиницу. Так «Систем оф Даун» и «System of a Down» сравниваются
 * в одном алфавите. Нечёткое сравнение с опечатками живёт в engine:search.
 */
object TextNormalizer {
    fun normalize(input: String): String {
        val lower = input.lowercase().replace('ё', 'е')
        val noMarks = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
        val latin = buildString(noMarks.length) { noMarks.forEach { append(CYRILLIC_TO_LATIN[it] ?: it) } }
        return latin.replace(NON_ALNUM, " ").trim().replace(SPACES, " ")
    }

    /** Без пробелов и с упрощёнными созвучиями: «sistem of daun» ≈ «system of a down». */
    fun phonetic(input: String): String {
        var s = normalize(input).replace(" ", "")
        PHONETIC_RULES.forEach { (from, to) -> s = s.replace(from, to) }
        return s.replace(REPEATS, "$1")
    }

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")
    private val SPACES = Regex("\\s+")
    private val REPEATS = Regex("(.)\\1+")

    private val PHONETIC_RULES =
        listOf(
            "ow" to "au",
            "ou" to "u",
            "ph" to "f",
            "ck" to "k",
            "c" to "k",
            "q" to "k",
            "w" to "v",
            "y" to "i",
            "x" to "ks",
            "a" to "",
            "e" to "",
            "o" to "",
        )

    private val CYRILLIC_TO_LATIN: Map<Char, String> =
        mapOf(
            'а' to "a",
            'б' to "b",
            'в' to "v",
            'г' to "g",
            'д' to "d",
            'е' to "e",
            'ж' to "zh",
            'з' to "z",
            'и' to "i",
            'й' to "y",
            'к' to "k",
            'л' to "l",
            'м' to "m",
            'н' to "n",
            'о' to "o",
            'п' to "p",
            'р' to "r",
            'с' to "s",
            'т' to "t",
            'у' to "u",
            'ф' to "f",
            'х' to "h",
            'ц' to "ts",
            'ч' to "ch",
            'ш' to "sh",
            'щ' to "sch",
            'ъ' to "",
            'ы' to "y",
            'ь' to "",
            'э' to "e",
            'ю' to "yu",
            'я' to "ya",
        )
}
