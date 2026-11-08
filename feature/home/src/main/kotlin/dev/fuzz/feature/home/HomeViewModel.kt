package dev.fuzz.feature.home

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fuzz.core.domain.library.ObserveLibraryTracksUseCase
import dev.fuzz.core.domain.library.ObserveRecentlyAddedUseCase
import dev.fuzz.core.domain.player.PlayerController
import dev.fuzz.core.domain.wave.WavePlayer
import dev.fuzz.core.domain.wave.WaveSettingsRepository
import dev.fuzz.core.domain.wave.WaveStartResult
import dev.fuzz.core.model.Track
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
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val isLoading: Boolean = true,
    val libraryEmpty: Boolean = false,
    /** Играет именно волна: кнопка на карточке тогда ставит паузу. */
    val isPlaying: Boolean = false,
    val familiarity: Int = WaveSettings.DEFAULT_FAMILIARITY,
    val recentlyAdded: ImmutableList<Track> = persistentListOf(),
) : UiState

sealed interface HomeIntent : UiIntent {
    data object PlayWave : HomeIntent

    data class PlayTrack(
        val index: Int,
    ) : HomeIntent
}

sealed interface HomeEffect : UiEffect {
    data object LibraryEmpty : HomeEffect
}

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        observeTracks: ObserveLibraryTracksUseCase,
        observeRecentlyAdded: ObserveRecentlyAddedUseCase,
        private val wave: WavePlayer,
        waveSettings: WaveSettingsRepository,
        private val player: PlayerController,
    ) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {
        init {
            combine(
                observeTracks().map { it.isEmpty() }.distinctUntilChanged(),
                observeRecentlyAdded(),
                combine(wave.status, player.state.map { it.isPlaying }.distinctUntilChanged()) { status, playing ->
                    status.active && playing
                }.distinctUntilChanged(),
                waveSettings.settings.map { it.familiarity }.distinctUntilChanged(),
            ) { empty, recent, playing, familiarity ->
                setState {
                    copy(
                        isLoading = false,
                        libraryEmpty = empty,
                        recentlyAdded = recent.toImmutableList(),
                        isPlaying = playing,
                        familiarity = familiarity,
                    )
                }
            }.launchIn(viewModelScope)
        }

        override fun onIntent(intent: HomeIntent) {
            when (intent) {
                HomeIntent.PlayWave -> {
                    if (wave.status.value.active) {
                        player.togglePlayPause()
                    } else {
                        viewModelScope.launch {
                            if (wave.start() == WaveStartResult.EmptyLibrary) sendEffect(HomeEffect.LibraryEmpty)
                        }
                    }
                }

                is HomeIntent.PlayTrack -> {
                    player.playQueue(currentState.recentlyAdded, intent.index)
                }
            }
        }
    }
