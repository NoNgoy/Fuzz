package dev.fuzz.engine.match

import dev.fuzz.core.common.text.TextNormalizer
import dev.fuzz.core.model.Track
import kotlin.math.abs
import kotlin.math.max

/** Что ищем: строка из чужого плейлиста. */
data class MatchQuery(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
)

sealed interface MatchResult {
    /** Уверенно тот же трек. */
    data class Found(
        val track: Track,
    ) : MatchResult

    /** Похоже, но лучше спросить: кандидаты по убыванию сходства. */
    data class Doubtful(
        val candidates: List<Track>,
    ) : MatchResult

    data object NotFound : MatchResult
}

/**
 * Сопоставляет треки чужих плейлистов с коллекцией (раздел 7 ТЗ). Сравнение идёт в одном алфавите
 * (кириллица транслитерируется), без «feat.», «Remastered» и подобных хвостов, с допуском на опечатки.
 * Индекс по триграммам названия и исполнителя отсекает кандидатов, чтобы не сравнивать всё со всем,
 * и при этом переживает опечатки, в отличие от индекса по целым словам.
 */
class TrackMatcher(
    library: List<Track>,
) {
    private val byTrigram = HashMap<String, MutableList<Entry>>()

    init {
        library.map(::Entry).forEach { entry ->
            entry.trigrams.forEach { byTrigram.getOrPut(it) { mutableListOf() } += entry }
        }
    }

    fun match(query: MatchQuery): MatchResult {
        val title = cleanTitle(query.title)
        val artists = artistKeys(query.artist)
        val hits = HashMap<Entry, Int>()
        trigrams(title, artists).forEach { gram -> byTrigram[gram]?.forEach { hits.merge(it, 1, Int::plus) } }
        val candidates = hits.entries.sortedByDescending { it.value }.take(MAX_SCORED).map { it.key }
        val scored =
            candidates
                .map { it to score(it, title, artists, query.durationMs) }
                .filter { it.second >= DOUBTFUL }
                .sortedByDescending { it.second }
        val best = scored.firstOrNull() ?: return MatchResult.NotFound
        val runnerUp = scored.getOrNull(1)?.second ?: 0.0
        return if (best.second >= FOUND && best.second - runnerUp >= MARGIN) {
            MatchResult.Found(best.first.track)
        } else {
            MatchResult.Doubtful(scored.take(MAX_CANDIDATES).map { it.first.track })
        }
    }

    private fun score(
        entry: Entry,
        title: String,
        artists: Set<String>,
        durationMs: Long?,
    ): Double {
        val titleScore = similarity(entry.title, title)
        // Без исполнителя в запросе (простой текст «Название») судим по названию, но строже.
        val artistScore =
            if (artists.isEmpty()) {
                NO_ARTIST_SCORE
            } else {
                artists.maxOf { a -> entry.artistKeys.maxOfOrNull { similarity(it, a) } ?: 0.0 }
            }
        val known = durationMs != null && entry.track.durationMs > 0
        val durationPenalty =
            if (known && abs(durationMs - entry.track.durationMs) > DURATION_TOLERANCE_MS) DURATION_PENALTY else 0.0
        return TITLE_WEIGHT * titleScore + ARTIST_WEIGHT * artistScore - durationPenalty
    }

    private class Entry(
        val track: Track,
    ) {
        val title = cleanTitle(track.title)
        val artistKeys = artistKeys(track.artistName)
        val trigrams = trigrams(title, artistKeys)
    }

    companion object {
        private const val TITLE_WEIGHT = 0.6
        private const val ARTIST_WEIGHT = 0.4
        private const val NO_ARTIST_SCORE = 0.75
        private const val FOUND = 0.93
        private const val DOUBTFUL = 0.72
        private const val MARGIN = 0.03
        private const val MAX_CANDIDATES = 3
        private const val DURATION_TOLERANCE_MS = 7_000L
        private const val DURATION_PENALTY = 0.15
        private const val PHONETIC_MATCH = 0.95
        private const val MAX_SCORED = 50
        private const val GRAM = 3

        private val NOISE =
            Regex(
                "\\s*[(\\[]\\s*(feat\\.?|ft\\.?|featuring|with|remaster(ed)?|\\d{4} remaster(ed)?|deluxe|" +
                    "bonus track|explicit|album version|single version|mono|stereo)[^)\\]]*[)\\]]",
                RegexOption.IGNORE_CASE,
            )
        private val DASH_NOISE = Regex("\\s+-\\s+(\\d{4}\\s+)?remaster(ed)?.*$", RegexOption.IGNORE_CASE)
        private val FEAT_TAIL = Regex("\\s+(feat\\.?|ft\\.?|featuring)\\s+.*$", RegexOption.IGNORE_CASE)
        private val ARTIST_SPLIT =
            Regex("\\s*(,|;|&|/|\\bfeat\\.?|\\bft\\.?|\\bx\\b|\\band\\b|\\bи\\b)\\s*", RegexOption.IGNORE_CASE)
        private val LEADING_THE = Regex("^the ")

        internal fun cleanTitle(raw: String): String =
            TextNormalizer.normalize(raw.replace(NOISE, "").replace(DASH_NOISE, "").replace(FEAT_TAIL, ""))

        internal fun artistKeys(raw: String): Set<String> =
            raw
                .split(ARTIST_SPLIT)
                .map { TextNormalizer.normalize(it).replace(LEADING_THE, "") }
                .filter { it.isNotEmpty() }
                .toSet()

        private fun trigrams(
            title: String,
            artists: Set<String>,
        ): Set<String> =
            buildSet {
                (listOf(title) + artists).forEach { text ->
                    val padded = " ${text.replace(" ", "")} "
                    for (i in 0..padded.length - GRAM) add(padded.substring(i, i + GRAM))
                }
            }

        /** 1 — совпало, 0.95 — звучит одинаково, дальше доля общих символов по Левенштейну. */
        internal fun similarity(
            a: String,
            b: String,
        ): Double =
            when {
                a == b -> 1.0
                a.isEmpty() || b.isEmpty() -> 0.0
                TextNormalizer.phonetic(a) == TextNormalizer.phonetic(b) -> PHONETIC_MATCH
                else -> 1.0 - levenshtein(a, b).toDouble() / max(a.length, b.length)
            }

        private fun levenshtein(
            a: String,
            b: String,
        ): Int {
            var previous = IntArray(b.length + 1) { it }
            var current = IntArray(b.length + 1)
            for (i in 1..a.length) {
                current[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                }
                previous = current.also { current = previous }
            }
            return previous[b.length]
        }
    }
}
