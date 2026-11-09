package dev.fuzz.core.data.search

import dev.fuzz.core.model.Track
import java.text.Normalizer

/**
 * Порядок онлайн-результатов из разных источников. Раньше они шли пачками по источникам, и по запросу
 * «Disturbed» тридцать песен с этим словом в названии от одного сервиса закрывали саму группу из другого.
 * Теперь выше всего треки нужного артиста, затем точные совпадения названия, затем остальное.
 * Внутри одной ступени порядок источника сохраняется.
 */
internal object OnlineRanking {
    fun <T> sort(
        query: String,
        items: List<T>,
        track: (T) -> Track,
    ): List<T> {
        val q = normalize(query)
        if (q.isEmpty()) return items
        return items.sortedBy { rank(q, track(it)) }
    }

    @Suppress("MagicNumber", "ReturnCount")
    private fun rank(
        q: String,
        track: Track,
    ): Int {
        val artist = normalize(track.artistName)
        val title = normalize(track.title)
        if (artist == q) return 0
        // «disturbed prayer»: в запросе и артист, и название.
        if (q.containsWord(artist) && q.containsWord(title)) return 0
        if (artist.isNotEmpty() && (q.startsWith("$artist ") || artist.startsWith("$q "))) return 1
        if (title == q) return 2
        if (artist.contains(q)) return 3
        if (title.contains(q)) return 4
        return 5
    }

    private fun String.containsWord(part: String): Boolean = part.isNotEmpty() && contains(part)

    fun normalize(text: String): String =
        Normalizer
            .normalize(text.lowercase(), Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}"), "")
            .replace('ё', 'е')
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
}
