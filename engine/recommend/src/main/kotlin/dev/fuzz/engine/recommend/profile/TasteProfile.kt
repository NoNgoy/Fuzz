package dev.fuzz.engine.recommend.profile

import dev.fuzz.core.model.Reaction
import dev.fuzz.core.model.TrackId
import dev.fuzz.engine.recommend.model.AudioFeatures
import kotlin.math.sqrt

/** Что известно о прослушиваниях одного трека. */
data class TrackStats(
    val plays: Int = 0,
    val completed: Int = 0,
    val skips: Int = 0,
    val earlySkips: Int = 0,
    val lastPlayedAtMs: Long? = null,
    val reaction: Reaction = Reaction.None,
    val playlistAdds: Int = 0,
    /** Затухающая сумма весов событий: больше нуля — трек нравится. */
    val affinity: Float = 0f,
) {
    val heard: Boolean get() = plays > 0
}

/**
 * Профиль вкуса: разреженные векторы тегов и артистов со знаком, средние аудиопризнаки
 * понравившегося и статистика по трекам. Неизменяемый снимок; копит его [TasteProfileBuilder].
 */
class TasteProfile(
    val tags: Map<String, Float>,
    val artists: Map<String, Float>,
    val audio: AudioFeatures?,
    val tracks: Map<TrackId, TrackStats>,
) {
    val tagNorm: Float = sqrt(tags.values.sumOf { (it * it).toDouble() }).toFloat()

    fun stats(id: TrackId): TrackStats = tracks[id] ?: EMPTY_STATS

    fun isDisliked(id: TrackId): Boolean = tracks[id]?.reaction == Reaction.Dislike

    /** Косинус между тегами трека и вкусом, от −1 до 1. */
    fun tagCosine(trackTags: Map<String, Float>): Float = cosine(trackTags, tags, tagNorm)

    /** Топ артистов с положительным весом, лучшие первыми. */
    fun topArtists(limit: Int): List<Pair<String, Float>> =
        artists.entries
            .filter { it.value > 0f }
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }

    fun topTags(limit: Int): List<String> =
        tags.entries
            .filter { it.value > 0f }
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }

    val isEmpty: Boolean get() = tags.isEmpty() && artists.isEmpty() && tracks.isEmpty()

    companion object {
        val EMPTY = TasteProfile(emptyMap(), emptyMap(), null, emptyMap())
        private val EMPTY_STATS = TrackStats()

        fun norm(vector: Map<String, Float>): Float = sqrt(vector.values.sumOf { (it * it).toDouble() }).toFloat()

        fun cosine(
            a: Map<String, Float>,
            b: Map<String, Float>,
            bNorm: Float = norm(b),
        ): Float {
            if (a.isEmpty() || b.isEmpty() || bNorm == 0f) return 0f
            val (small, large) = if (a.size <= b.size) a to b else b to a
            var dot = 0f
            for ((key, value) in small) {
                val other = large[key] ?: continue
                dot += value * other
            }
            val aNorm = norm(a)
            return if (aNorm == 0f) 0f else dot / (aNorm * bNorm)
        }
    }
}
