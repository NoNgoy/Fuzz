package dev.fuzz.feature.wave

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fuzz.core.domain.player.PlayerController
import dev.fuzz.core.domain.wave.WavePlayer
import dev.fuzz.core.domain.wave.WaveSettingsRepository
import dev.fuzz.core.domain.wave.WaveShare
import dev.fuzz.core.domain.wave.WaveStartResult
import dev.fuzz.core.model.wave.WaveActivity
import dev.fuzz.core.model.wave.WaveCharacter
import dev.fuzz.core.model.wave.WaveMood
import dev.fuzz.core.model.wave.WaveSettings
import dev.fuzz.core.ui.mvi.MviViewModel
import dev.fuzz.core.ui.mvi.UiEffect
import dev.fuzz.core.ui.mvi.UiIntent
import dev.fuzz.core.ui.mvi.UiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WaveState(
    val settings: WaveSettings = WaveSettings(),
    /** Положение ползунка, пока его тянут: в хранилище уходит только по отпусканию. */
    val familiarity: Int = settings.familiarity,
    val mix: ImmutableList<WaveShare> = persistentListOf(),
    val waveActive: Boolean = false,
    val playing: Boolean = false,
    val starting: Boolean = false,
) : UiState

sealed interface WaveIntent : UiIntent {
    data class DragFamiliarity(
        val value: Int,
    ) : WaveIntent

    data object CommitFamiliarity : WaveIntent

    data class ToggleActivity(
        val activity: WaveActivity,
    ) : WaveIntent

    data class ToggleMood(
        val mood: WaveMood,
    ) : WaveIntent

    data class ToggleCharacter(
        val character: WaveCharacter,
    ) : WaveIntent

    data object Reset : WaveIntent

    data object PlayPause : WaveIntent
}

sealed interface WaveEffect : UiEffect {
    data object LibraryEmpty : WaveEffect
}

@HiltViewModel
class WaveViewModel
    @Inject
    constructor(
        private val repository: WaveSettingsRepository,
        private val wave: WavePlayer,
        private val player: PlayerController,
    ) : MviViewModel<WaveState, WaveIntent, WaveEffect>(
            WaveState(mix = wave.mix(WaveSettings.DEFAULT_FAMILIARITY).toImmutableList()),
        ) {
        init {
            // Настройки и плеер слушаются раздельно: тики позиции плеера не должны сбрасывать ползунок под пальцем.
            repository.settings
                .onEach { settings ->
                    setState {
                        copy(
                            settings = settings,
                            familiarity = settings.familiarity,
                            mix = wave.mix(settings.familiarity).toImmutableList(),
                        )
                    }
                }.launchIn(viewModelScope)
            combine(wave.status, player.state.map { it.isPlaying }.distinctUntilChanged()) { status, playing ->
                setState {
                    copy(waveActive = status.active, playing = status.active && playing, starting = status.starting)
                }
            }.launchIn(viewModelScope)
        }

        override fun onIntent(intent: WaveIntent) {
            when (intent) {
                is WaveIntent.DragFamiliarity -> {
                    setState { copy(familiarity = intent.value, mix = wave.mix(intent.value).toImmutableList()) }
                }

                WaveIntent.CommitFamiliarity -> {
                    val value = currentState.familiarity
                    // Пресет характера задаёт свою пропорцию, ползунок его отменяет.
                    update { it.copy(familiarity = value, character = null) }
                }

                is WaveIntent.ToggleActivity -> {
                    update { it.copy(activity = intent.activity.takeIf { a -> a != it.activity }) }
                }

                is WaveIntent.ToggleMood -> {
                    update { it.copy(mood = intent.mood.takeIf { m -> m != it.mood }) }
                }

                is WaveIntent.ToggleCharacter -> {
                    update { it.copy(character = intent.character.takeIf { c -> c != it.character }) }
                }

                WaveIntent.Reset -> {
                    update { WaveSettings() }
                }

                WaveIntent.PlayPause -> {
                    playPause()
                }
            }
        }

        private fun playPause() {
            if (currentState.waveActive) {
                player.togglePlayPause()
                return
            }
            viewModelScope.launch {
                if (wave.start() == WaveStartResult.EmptyLibrary) sendEffect(WaveEffect.LibraryEmpty)
            }
        }

        private fun update(transform: (WaveSettings) -> WaveSettings) {
            viewModelScope.launch { repository.update(transform) }
        }
    }
