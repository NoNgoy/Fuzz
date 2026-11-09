@file:Suppress("MagicNumber") // таблица пресетов: децибелы и частоты

package dev.fuzz.core.domain.player

/** Пресеты эквалайзера: усиление в децибелах на опорных частотах, к полосам телефона они подгоняются сами. */
enum class EqualizerPreset(
    val gainsDb: List<Float>,
) {
    Flat(listOf(0f, 0f, 0f, 0f, 0f)),
    Bass(listOf(6f, 4f, 0f, 0f, 0f)),
    Vocal(listOf(-2f, 0f, 3f, 4f, 1f)),
    Rock(listOf(4f, 2f, -1f, 2f, 4f)),
    Electronic(listOf(5f, 3f, 0f, 2f, 4f)),
    Acoustic(listOf(2f, 1f, 1f, 3f, 2f)),

    /** Тихо ночью: срезаны низы и звонкие верха, чтобы не мешать соседям и не резать уши. */
    Night(listOf(-4f, -1f, 1f, 0f, -3f)),
    ;

    companion object {
        /** Опорные частоты [gainsDb], Гц: как у пятиполосного эквалайзера Android. */
        val REFERENCE_HZ = listOf(60, 230, 910, 3_600, 14_000)
    }
}
