package dev.fuzz.feature.player

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fuzz.core.domain.player.PlayerController
import dev.fuzz.core.domain.player.SleepTimer
import dev.fuzz.core.domain.player.SleepTimerState
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
    val sleep: SleepTimerState = SleepTimerState.Off,
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

    data object ToggleLike : ReactionIntent

    /** Двойной тап по обложке: только ставит лайк, не снимает. */
    data object Like : ReactionIntent

    data object ToggleDislike : ReactionIntent

    data object OpenLyrics : PlayerIntent

    data object OpenWhy : PlayerIntent

    data object OpenSleep : SleepIntent

    data object OpenSound : PlayerIntent

    data class StartSleep(
        val minutes: Int,
    ) : SleepIntent

    data object SleepAfterTrack : SleepIntent

    data object CancelSleep : SleepIntent
}

/** Лайк и дизлайк текущего трека. */
sealed interface ReactionIntent : PlayerIntent

/** Таймер сна: открыть шторку и команды из неё. */
sealed interface SleepIntent : PlayerIntent

sealed interface PlayerEffect : UiEffect {
    data object ShowLyrics : PlayerEffect

    data object ShowWhy : PlayerEffect

    data object ShowSleep : PlayerEffect

    data object ShowSound : PlayerEffect
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
        private val sleepTimer: SleepTimer,
    ) : MviViewModel<PlayerUiState, PlayerIntent, PlayerEffect>(PlayerUiState()) {
        init {
            player.state.onEach { setState { copy(playback = it) } }.launchIn(viewModelScope)
            sleepTimer.state.onEach { setState { copy(sleep = it) } }.launchIn(viewModelScope)
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
                is ReactionIntent -> react(intent)
                PlayerIntent.OpenLyrics -> sendEffect(PlayerEffect.ShowLyrics)
                PlayerIntent.OpenWhy -> sendEffect(PlayerEffect.ShowWhy)
                is SleepIntent -> sleep(intent)
                PlayerIntent.OpenSound -> sendEffect(PlayerEffect.ShowSound)
            }
        }

        private fun sleep(intent: SleepIntent) =
            when (intent) {
                PlayerIntent.OpenSleep -> sendEffect(PlayerEffect.ShowSleep)
                is PlayerIntent.StartSleep -> sleepTimer.start(intent.minutes)
                PlayerIntent.SleepAfterTrack -> sleepTimer.stopAfterTrack()
                PlayerIntent.CancelSleep -> sleepTimer.cancel()
            }

        private fun react(intent: ReactionIntent) =
            when (intent) {
                PlayerIntent.ToggleLike -> react(Reaction.Like)
                PlayerIntent.Like -> if (currentState.reaction != Reaction.Like) react(Reaction.Like) else Unit
                PlayerIntent.ToggleDislike -> react(Reaction.Dislike)
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
