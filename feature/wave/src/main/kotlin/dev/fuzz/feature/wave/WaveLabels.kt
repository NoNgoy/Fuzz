package dev.fuzz.feature.wave

import androidx.annotation.StringRes
import dev.fuzz.core.domain.wave.WaveSource
import dev.fuzz.core.model.wave.WaveActivity
import dev.fuzz.core.model.wave.WaveCharacter
import dev.fuzz.core.model.wave.WaveMood

@get:StringRes
internal val WaveActivity.label: Int
    get() =
        when (this) {
            WaveActivity.WakingUp -> R.string.feature_wave_activity_waking_up
            WaveActivity.Commuting -> R.string.feature_wave_activity_commuting
            WaveActivity.Working -> R.string.feature_wave_activity_working
            WaveActivity.Workout -> R.string.feature_wave_activity_workout
            WaveActivity.Relaxing -> R.string.feature_wave_activity_relaxing
            WaveActivity.FallingAsleep -> R.string.feature_wave_activity_falling_asleep
        }

@get:StringRes
internal val WaveMood.label: Int
    get() =
        when (this) {
            WaveMood.Energetic -> R.string.feature_wave_mood_energetic
            WaveMood.Happy -> R.string.feature_wave_mood_happy
            WaveMood.Calm -> R.string.feature_wave_mood_calm
            WaveMood.Sad -> R.string.feature_wave_mood_sad
            WaveMood.Aggressive -> R.string.feature_wave_mood_aggressive
        }

@get:StringRes
internal val WaveCharacter.label: Int
    get() =
        when (this) {
            WaveCharacter.Favorite -> R.string.feature_wave_character_favorite
            WaveCharacter.Unfamiliar -> R.string.feature_wave_character_unfamiliar
            WaveCharacter.Popular -> R.string.feature_wave_character_popular
        }

@get:StringRes
internal val WaveSource.label: Int
    get() =
        when (this) {
            WaveSource.Favorites -> R.string.feature_wave_source_favorites
            WaveSource.Unheard -> R.string.feature_wave_source_unheard
            WaveSource.Similar -> R.string.feature_wave_source_similar
            WaveSource.OpenCatalog -> R.string.feature_wave_source_open_catalog
            WaveSource.Preview -> R.string.feature_wave_source_preview
        }

/** Подпись положения ползунка на карточке волны, как в макете. */
@StringRes
internal fun mixLabel(familiarity: Int): Int =
    when {
        familiarity < FAMILIAR_BELOW -> R.string.feature_wave_mix_familiar
        familiarity > ALL_NEW_ABOVE -> R.string.feature_wave_mix_all_new
        else -> R.string.feature_wave_mix_new
    }

private const val FAMILIAR_BELOW = 20
private const val ALL_NEW_ABOVE = 80
