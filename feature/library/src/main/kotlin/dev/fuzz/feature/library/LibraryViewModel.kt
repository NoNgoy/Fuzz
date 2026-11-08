package dev.fuzz.feature.library

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fuzz.core.domain.library.ObserveLibraryTracksUseCase
import dev.fuzz.core.domain.player.PlayerController
import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId
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
import javax.inject.Inject

data class LibraryState(
    val isLoading: Boolean = true,
    val tracks: ImmutableList<Track> = persistentListOf(),
    val currentTrackId: TrackId? = null,
) : UiState

sealed interface LibraryIntent : UiIntent {
    data class PlayTrack(
        val index: Int,
    ) : LibraryIntent

    data object ShuffleAll : LibraryIntent
}

sealed interface LibraryEffect : UiEffect

@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        observeTracks: ObserveLibraryTracksUseCase,
        private val player: PlayerController,
    ) : MviViewModel<LibraryState, LibraryIntent, LibraryEffect>(LibraryState()) {
        init {
            combine(
                observeTracks(),
                player.state.map { it.currentTrack?.id }.distinctUntilChanged(),
            ) { tracks, currentId ->
                setState { copy(isLoading = false, tracks = tracks.toImmutableList(), currentTrackId = currentId) }
            }.launchIn(viewModelScope)
        }

        override fun onIntent(intent: LibraryIntent) {
            val tracks = currentState.tracks
            when (intent) {
                is LibraryIntent.PlayTrack -> {
                    player.setShuffle(false)
                    player.playQueue(tracks, intent.index)
                }

                LibraryIntent.ShuffleAll -> {
                    if (tracks.isNotEmpty()) {
                        player.playQueue(tracks.shuffled(), 0)
                    }
                }
            }
        }
    }
