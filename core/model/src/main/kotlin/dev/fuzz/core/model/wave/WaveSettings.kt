package dev.fuzz.core.model.wave

import dev.fuzz.core.model.AlbumId
import dev.fuzz.core.model.TrackId

/** Занятие из настроек волны (раздел 4.1 ТЗ): задаёт диапазон темпа, энергии и теги. */
enum class WaveActivity { WakingUp, Commuting, Working, Workout, Relaxing, FallingAsleep }

/** Настроение: энергия, лад и теги настроения. */
enum class WaveMood { Energetic, Happy, Calm, Sad, Aggressive }

/** Быстрые пресеты поверх ползунка «Знакомое ↔ Новое». */
enum class WaveCharacter { Favorite, Unfamiliar, Popular }

/**
 * Настройки волны. [familiarity] — ползунок: 0 только знакомое, 100 только новое.
 * Жанры и языки сравниваются в нижнем регистре; пустой набор значит «без ограничений».
 */
data class WaveSettings(
    val familiarity: Int = DEFAULT_FAMILIARITY,
    val activity: WaveActivity? = null,
    val mood: WaveMood? = null,
    val character: WaveCharacter? = null,
    val genres: Set<String> = emptySet(),
    val excludedGenres: Set<String> = emptySet(),
    val languages: Set<String> = emptySet(),
    val excludedLanguages: Set<String> = emptySet(),
) {
    init {
        require(familiarity in 0..MAX_FAMILIARITY) { "familiarity должен быть в 0..100, а не $familiarity" }
    }

    companion object {
        const val DEFAULT_FAMILIARITY = 50
        const val MAX_FAMILIARITY = 100
    }
}

/** Сохранённый пресет: «Спортзал», «Дорога домой». */
data class WavePreset(
    val name: String,
    val settings: WaveSettings,
)

/** Откуда стартует волна: «Моя волна» или волна по треку, артисту, альбому, жанру, плейлисту. */
sealed interface WaveSeed {
    data object MyWave : WaveSeed

    data class Track(
        val trackId: TrackId,
    ) : WaveSeed

    data class Artist(
        val name: String,
    ) : WaveSeed

    data class Album(
        val albumId: AlbumId,
    ) : WaveSeed

    data class Genre(
        val tag: String,
    ) : WaveSeed

    data class Playlist(
        val name: String,
        val trackIds: List<TrackId>,
    ) : WaveSeed
}
