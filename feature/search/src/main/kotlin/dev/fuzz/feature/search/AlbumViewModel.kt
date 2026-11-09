package dev.fuzz.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fuzz.core.domain.artist.AlbumProfile
import dev.fuzz.core.domain.artist.ArtistPages
import dev.fuzz.core.domain.player.PlayerController
import dev.fuzz.core.domain.search.OnlineSearch
import dev.fuzz.core.model.Track
import dev.fuzz.core.ui.mvi.MviViewModel
import dev.fuzz.core.ui.mvi.UiEffect
import dev.fuzz.core.ui.mvi.UiIntent
import dev.fuzz.core.ui.mvi.UiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumState(
    val album: AlbumProfile,
    val isLoading: Boolean = true,
    val tracks: ImmutableList<Track> = persistentListOf(),
    val preparing: Boolean = false,
) : UiState

sealed interface AlbumIntent : UiIntent {
    data class Play(
        val index: Int,
    ) : AlbumIntent
}

sealed interface PlaybackEffect : UiEffect {
    /** Ни для одного трека не нашлось звука. */
    data object NotPlayable : PlaybackEffect
}

@HiltViewModel
class AlbumViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val pages: ArtistPages,
        private val online: OnlineSearch,
        private val player: PlayerController,
    ) : MviViewModel<AlbumState, AlbumIntent, PlaybackEffect>(
            AlbumState(savedState.toRoute<AlbumRoute>().toProfile()),
        ) {
        init {
            viewModelScope.launch {
                val tracks = pages.album(currentState.album)
                setState { copy(isLoading = false, tracks = tracks.toImmutableList()) }
            }
        }

        override fun onIntent(intent: AlbumIntent) {
            when (intent) {
                is AlbumIntent.Play -> {
                    if (currentState.tracks.isEmpty() || currentState.preparing) return
                    setState { copy(preparing = true) }
                    viewModelScope.launch {
                        val queue = preparePlayback(online, currentState.tracks, intent.index)
                        setState { copy(preparing = false) }
                        if (queue.isEmpty()) {
                            sendEffect(PlaybackEffect.NotPlayable)
                        } else {
                            player.setShuffle(false)
                            player.playQueue(queue)
                        }
                    }
                }
            }
        }
    }
