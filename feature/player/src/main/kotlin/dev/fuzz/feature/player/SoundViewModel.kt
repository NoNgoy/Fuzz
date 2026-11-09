package dev.fuzz.feature.player

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fuzz.core.domain.player.EqualizerBands
import dev.fuzz.core.domain.player.EqualizerPreset
import dev.fuzz.core.domain.player.SoundEffects
import dev.fuzz.core.domain.player.SoundSettings
import dev.fuzz.core.domain.player.SoundSettingsRepository
import dev.fuzz.core.domain.player.gainsFor
import dev.fuzz.core.ui.mvi.MviViewModel
import dev.fuzz.core.ui.mvi.UiEffect
import dev.fuzz.core.ui.mvi.UiIntent
import dev.fuzz.core.ui.mvi.UiState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SoundState(
    val settings: SoundSettings = SoundSettings(),
    /** Полосы эквалайзера телефона; null — эффекты пока недоступны. */
    val bands: EqualizerBands? = null,
) : UiState {
    /** Что сейчас стоит на полосах: пресет или ручная настройка. */
    val gains: List<Float> get() = bands?.let(settings::gainsFor).orEmpty()
}

sealed interface SoundIntent : UiIntent {
    data class SetEqualizer(
        val enabled: Boolean,
    ) : SoundIntent

    data class PickPreset(
        val preset: EqualizerPreset,
    ) : SoundIntent

    /** Сдвиг полосы переводит эквалайзер в ручную настройку, начиная с того, что было на полосах. */
    data class SetBand(
        val index: Int,
        val db: Float,
    ) : SoundIntent

    data class SetBassBoost(
        val strength: Float,
    ) : SoundIntent

    data class SetFade(
        val seconds: Int,
    ) : SoundIntent

    data class SetSkipSilence(
        val enabled: Boolean,
    ) : SoundIntent

    data class SetNormalize(
        val enabled: Boolean,
    ) : SoundIntent
}

sealed interface SoundEffect : UiEffect

@HiltViewModel
class SoundViewModel
    @Inject
    constructor(
        private val repository: SoundSettingsRepository,
        effects: SoundEffects,
    ) : MviViewModel<SoundState, SoundIntent, SoundEffect>(SoundState()) {
        init {
            repository.settings.onEach { setState { copy(settings = it) } }.launchIn(viewModelScope)
            effects.bands.onEach { setState { copy(bands = it) } }.launchIn(viewModelScope)
        }

        override fun onIntent(intent: SoundIntent) {
            when (intent) {
                is SoundIntent.SetEqualizer -> {
                    update { copy(equalizerEnabled = intent.enabled) }
                }

                is SoundIntent.PickPreset -> {
                    update { copy(equalizerEnabled = true, preset = intent.preset) }
                }

                is SoundIntent.SetBand -> {
                    val gains = currentState.gains.toMutableList()
                    if (intent.index !in gains.indices) return
                    gains[intent.index] = intent.db
                    update { copy(equalizerEnabled = true, preset = null, customGainsDb = gains) }
                }

                is SoundIntent.SetBassBoost -> {
                    update { copy(bassBoost = intent.strength.coerceIn(0f, 1f)) }
                }

                is SoundIntent.SetFade -> {
                    update { copy(fadeSeconds = intent.seconds.coerceIn(0, SoundSettings.MAX_FADE_SECONDS)) }
                }

                is SoundIntent.SetSkipSilence -> {
                    update { copy(skipSilence = intent.enabled) }
                }

                is SoundIntent.SetNormalize -> {
                    update { copy(normalize = intent.enabled) }
                }
            }
        }

        private fun update(transform: SoundSettings.() -> SoundSettings) {
            // Сразу в состояние, чтобы ползунок не дёргался, пока пишет DataStore.
            setState { copy(settings = settings.transform()) }
            viewModelScope.launch { repository.update(transform) }
        }
    }
