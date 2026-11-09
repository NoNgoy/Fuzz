package dev.fuzz.feature.player

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fuzz.core.domain.lyrics.LyricsRepository
import dev.fuzz.core.domain.lyrics.LyricsResult
import dev.fuzz.core.domain.player.PlayerController
import dev.fuzz.core.domain.plugin.LyricsLine
import dev.fuzz.core.ui.mvi.MviViewModel
import dev.fuzz.core.ui.mvi.UiEffect
import dev.fuzz.core.ui.mvi.UiIntent
import dev.fuzz.core.ui.mvi.UiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

sealed interface LyricsContent {
    data object Loading : LyricsContent

    data class Text(
        val lines: ImmutableList<LyricsLine>,
        val synced: Boolean,
        val source: String,
    ) : LyricsContent

    data object NotFound : LyricsContent

    data object Offline : LyricsContent

    /** Ничего не играет. */
    data object Idle : LyricsContent
}

data class LyricsState(
    val title: String = "",
    val artist: String = "",
    val content: LyricsContent = LyricsContent.Idle,
    /** Строка, которая звучит сейчас; -1 до первой строки или у несинхронного текста. */
    val currentLine: Int = -1,
) : UiState

sealed interface LyricsIntent : UiIntent {
    /** Тап по строке синхронного текста: перемотка к ней. */
    data class SeekToLine(
        val index: Int,
    ) : LyricsIntent
}

sealed interface LyricsEffect : UiEffect

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LyricsViewModel
    @Inject
    constructor(
        private val player: PlayerController,
        private val repository: LyricsRepository,
    ) : MviViewModel<LyricsState, LyricsIntent, LyricsEffect>(LyricsState()) {
        init {
            player.state
                .map { it.currentTrack }
                .distinctUntilChanged { a, b -> a?.id == b?.id }
                .mapLatest { track ->
                    if (track == null) {
                        LyricsState()
                    } else {
                        setState { LyricsState(track.title, track.artistName, LyricsContent.Loading) }
                        LyricsState(track.title, track.artistName, repository.lyrics(track).toContent())
                    }
                }.onEach { loaded -> setState { loaded } }
                .launchIn(viewModelScope)
            player.state
                .map { it.positionMs }
                .onEach { position -> setState { copy(currentLine = lineAt(position)) } }
                .launchIn(viewModelScope)
        }

        override fun onIntent(intent: LyricsIntent) {
            when (intent) {
                is LyricsIntent.SeekToLine -> {
                    val text = currentState.content as? LyricsContent.Text ?: return
                    text.lines.getOrNull(intent.index)?.timeMs?.let(player::seekTo)
                }
            }
        }

        private fun LyricsState.lineAt(positionMs: Long): Int {
            val text = content as? LyricsContent.Text ?: return -1
            if (!text.synced) return -1
            return text.lines.indexOfLast { (it.timeMs ?: Long.MAX_VALUE) <= positionMs }
        }

        private fun LyricsResult.toContent(): LyricsContent =
            when (this) {
                is LyricsResult.Found -> LyricsContent.Text(lyrics.lines.toImmutableList(), lyrics.synced, source)
                LyricsResult.NotFound -> LyricsContent.NotFound
                LyricsResult.Unavailable -> LyricsContent.Offline
            }
    }
