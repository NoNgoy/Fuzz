package dev.fuzz.feature.player

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fuzz.core.domain.player.PlayerController
import dev.fuzz.core.domain.reaction.ReactionRepository
import dev.fuzz.core.domain.reaction.ToggleReactionUseCase
import dev.fuzz.core.model.PlaybackState
import dev.fuzz.core.model.Reaction
import dev.fuzz.core.model.RepeatMode
import dev.fuzz.core.ui.mvi.MviViewModel
import dev.fuzz.core.ui.mvi.UiEffect
import dev.fuzz.core.ui.mvi.UiIntent
import dev.fuzz.core.ui.mvi.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val playback: PlaybackState = PlaybackState(),
    val reaction: Reaction = Reaction.None,
) : UiState

/** Разделы плеера, которые появятся на следующих этапах. */
enum class UpcomingFeature { Lyrics, Why, Sound, Sleep }

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

    data object ToggleLike : PlayerIntent

    /** Двойной тап по обложке: только ставит лайк, не снимает. */
    data object Like : PlayerIntent

    data object ToggleDislike : PlayerIntent

    data class OpenUpcoming(
        val feature: UpcomingFeature,
    ) : PlayerIntent
}

sealed interface PlayerEffect : UiEffect {
    data class ShowUpcoming(
        val feature: UpcomingFeature,
    ) : PlayerEffect
}

/** Общая ViewModel мини-плеера и полноэкранного плеера: оба читают одно состояние MediaSession. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        private val player: PlayerController,
        private val reactions: ReactionRepository,
        private val toggleReaction: ToggleReactionUseCase,
    ) : MviViewModel<PlayerUiState, PlayerIntent, PlayerEffect>(PlayerUiState()) {
        init {
            player.state.onEach { setState { copy(playback = it) } }.launchIn(viewModelScope)
            player.state
                .map { it.currentTrack?.id }
                .distinctUntilChanged()
                .flatMapLatest { id -> if (id == null) flowOf(Reaction.None) else reactions.observe(id) }
                .onEach { setState { copy(reaction = it) } }
                .launchIn(viewModelScope)
        }

        override fun onIntent(intent: PlayerIntent) {
            val playback = currentState.playback
            when (intent) {
                PlayerIntent.TogglePlay -> player.togglePlayPause()
                PlayerIntent.Next -> player.skipToNext()
                PlayerIntent.Previous -> player.skipToPrevious()
                is PlayerIntent.Seek -> player.seekTo((intent.fraction * playback.durationMs).toLong())
                PlayerIntent.ToggleShuffle -> player.setShuffle(!playback.shuffle)
                PlayerIntent.CycleRepeat -> player.setRepeatMode(playback.repeatMode.next())
                is PlayerIntent.PlayQueueItem -> player.skipToQueueItem(intent.index)
                PlayerIntent.ToggleLike -> react(Reaction.Like)
                PlayerIntent.Like -> if (currentState.reaction != Reaction.Like) react(Reaction.Like)
                PlayerIntent.ToggleDislike -> react(Reaction.Dislike)
                is PlayerIntent.OpenUpcoming -> sendEffect(PlayerEffect.ShowUpcoming(intent.feature))
            }
        }

        private fun react(reaction: Reaction) {
            val id = currentState.playback.currentTrack?.id ?: return
            viewModelScope.launch {
                val result = toggleReaction(id, reaction)
                // Дизлайк убирает трек: сразу играет следующий (раздел 6, жесты плеера).
                if (result == Reaction.Dislike) player.skipToNext()
            }
        }
    }

private fun RepeatMode.next() =
    when (this) {
        RepeatMode.Off -> RepeatMode.All
        RepeatMode.All -> RepeatMode.One
        RepeatMode.One -> RepeatMode.Off
    }
