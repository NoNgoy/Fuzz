package dev.fuzz.feature.player

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fuzz.core.domain.player.PlayerController
import dev.fuzz.core.domain.wave.WavePlayer
import dev.fuzz.core.domain.wave.WaveReason
import dev.fuzz.core.ui.mvi.MviViewModel
import dev.fuzz.core.ui.mvi.UiEffect
import dev.fuzz.core.ui.mvi.UiIntent
import dev.fuzz.core.ui.mvi.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

sealed interface WhyContent {
    data object Loading : WhyContent

    data class Explained(
        val reason: WaveReason,
    ) : WhyContent

    /** Трек включён не волной: объяснять нечего. */
    data object NotWave : WhyContent

    /** Ничего не играет. */
    data object Idle : WhyContent
}

data class WhyState(
    val title: String = "",
    val artist: String = "",
    val content: WhyContent = WhyContent.Idle,
) : UiState

sealed interface WhyIntent : UiIntent

sealed interface WhyEffect : UiEffect

/** «Почему этот трек?»: спрашивает у волны причину для звучащего трека и обновляется при смене трека. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WhyViewModel
    @Inject
    constructor(
        player: PlayerController,
        private val wave: WavePlayer,
    ) : MviViewModel<WhyState, WhyIntent, WhyEffect>(WhyState()) {
        init {
            player.state
                .map { it.currentTrack }
                .distinctUntilChanged { a, b -> a?.id == b?.id }
                .mapLatest { track ->
                    if (track == null) {
                        WhyState()
                    } else {
                        setState { WhyState(track.title, track.artistName, WhyContent.Loading) }
                        val reason = wave.reason(track.id)
                        WhyState(
                            track.title,
                            track.artistName,
                            if (reason == null) WhyContent.NotWave else WhyContent.Explained(reason),
                        )
                    }
                }.onEach { loaded -> setState { loaded } }
                .launchIn(viewModelScope)
        }

        override fun onIntent(intent: WhyIntent) = Unit
    }
