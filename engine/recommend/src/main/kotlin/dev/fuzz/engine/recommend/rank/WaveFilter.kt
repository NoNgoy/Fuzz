package dev.fuzz.engine.recommend.rank

import dev.fuzz.core.model.wave.WaveSettings
import dev.fuzz.engine.recommend.model.WaveTrack

/**
 * Фильтр кандидатов по языку и жанрам (раздел 4.1 ТЗ). Исключения строгие. Включения строгие
 * только для треков, о которых это известно: трек без тегов или языка не выкидывается, иначе
 * коллекция без метаданных дала бы пустую волну.
 */
internal class WaveFilter(
    settings: WaveSettings,
) {
    private val genres = settings.genres.map(String::lowercase).toSet()
    private val excludedGenres = settings.excludedGenres.map(String::lowercase).toSet()
    private val languages = settings.languages.map(String::lowercase).toSet()
    private val excludedLanguages = settings.excludedLanguages.map(String::lowercase).toSet()

    fun accepts(track: WaveTrack): Boolean {
        val tags = track.tags.filterValues { it >= TAG_FLOOR }.keys
        val language = track.language?.lowercase()
        return tags.none { it in excludedGenres } &&
            (language == null || language !in excludedLanguages) &&
            (genres.isEmpty() || tags.isEmpty() || tags.any { it in genres }) &&
            (languages.isEmpty() || language == null || language in languages)
    }

    private companion object {
        const val TAG_FLOOR = 0.2f
    }
}
