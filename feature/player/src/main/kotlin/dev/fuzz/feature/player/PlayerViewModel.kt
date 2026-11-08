package dev.fuzz.feature.player

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fuzz.core.domain.player.PlayerController
import dev.fuzz.core.model.PlaybackState
import dev.fuzz.core.model.RepeatMode
import dev.fuzz.core.ui.mvi.MviViewModel
import dev.fuzz.core.ui.mvi.UiEffect
import dev.fuzz.core.ui.mvi.UiIntent
import dev.fuzz.core.ui.mvi.UiState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

data class PlayerUiState(
    val playback: PlaybackState = PlaybackState(),
) : UiState

sealed interface PlayerIntent : UiIntent {
    data object TogglePlay : PlayerIntent

    data object Next : PlayerIntent

    data object Previous : PlayerIntent

    data class Seek(
        val fraction: Float,
    ) : PlayerIntent

    data object ToggleShuffle : PlayerIntent

    data object CycleRepeat : PlayerIntent

    data class PlayQueueItem(
        val index: Int,
    ) : PlayerIntent
}

sealed interface PlayerEffect : UiEffect

/** Общая ViewModel мини-плеера и полноэкранного плеера: оба читают одно состояние MediaSession. */
@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        private val player: PlayerController,
    ) : MviViewModel<PlayerUiState, PlayerIntent, PlayerEffect>(PlayerUiState()) {
        init {
            player.state.onEach { setState { copy(playback = it) } }.launchIn(viewModelScope)
        }

        override fun onIntent(intent: PlayerIntent) {
            val playback = currentState.playback
            when (intent) {
                PlayerIntent.TogglePlay -> {
                    player.togglePlayPause()
                }

                PlayerIntent.Next -> {
                    player.skipToNext()
                }

                PlayerIntent.Previous -> {
                    player.skipToPrevious()
                }

                is PlayerIntent.Seek -> {
                    player.seekTo((intent.fraction * playback.durationMs).toLong())
                }

                PlayerIntent.ToggleShuffle -> {
                    player.setShuffle(!playback.shuffle)
                }

                PlayerIntent.CycleRepeat -> {
                    player.setRepeatMode(
                        when (playback.repeatMode) {
                            RepeatMode.Off -> RepeatMode.All
                            RepeatMode.All -> RepeatMode.One
                            RepeatMode.One -> RepeatMode.Off
                        },
                    )
                }

                is PlayerIntent.PlayQueueItem -> {
                    player.skipToQueueItem(intent.index)
                }
            }
        }
    }
