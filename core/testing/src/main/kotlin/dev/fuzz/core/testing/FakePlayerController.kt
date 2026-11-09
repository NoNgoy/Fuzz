package dev.fuzz.core.testing

import dev.fuzz.core.domain.player.PlayerController
import dev.fuzz.core.model.PlaybackState
import dev.fuzz.core.model.RepeatMode
import dev.fuzz.core.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Плеер для тестов: очередь и позиция живут в памяти, команды применяются сразу. */
class FakePlayerController : PlayerController {
    private val mutable = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutable

    fun setPosition(positionMs: Long) = mutable.update { it.copy(positionMs = positionMs) }

    override fun playQueue(
        tracks: List<Track>,
        startIndex: Int,
        startPositionMs: Long,
    ) = mutable.update {
        it.copy(queue = tracks, positionMs = startPositionMs, isPlaying = true).at(startIndex)
    }

    override fun playNext(tracks: List<Track>) =
        mutable.update { s ->
            s.copy(queue = s.queue.take(s.currentIndex + 1) + tracks + s.queue.drop(s.currentIndex + 1))
        }

    override fun addToQueue(tracks: List<Track>) = mutable.update { it.copy(queue = it.queue + tracks) }

    override fun moveInQueue(
        from: Int,
        to: Int,
    ) = mutable.update { s ->
        val queue = s.queue.toMutableList().apply { add(to, removeAt(from)) }
        s.copy(queue = queue)
    }

    override fun removeFromQueue(index: Int) =
        mutable.update { s ->
            val queue = s.queue.toMutableList().apply { removeAt(index) }
            val current = if (index < s.currentIndex) s.currentIndex - 1 else s.currentIndex
            s.copy(queue = queue).at(current)
        }

    override fun skipToQueueItem(index: Int) = mutable.update { it.copy(positionMs = 0).at(index) }

    override fun togglePlayPause() = mutable.update { it.copy(isPlaying = !it.isPlaying) }

    override fun pause() = mutable.update { it.copy(isPlaying = false) }

    override fun skipToNext() = mutable.update { it.copy(positionMs = 0).at(it.currentIndex + 1) }

    override fun skipToPrevious() = mutable.update { it.copy(positionMs = 0).at(it.currentIndex - 1) }

    override fun seekTo(positionMs: Long) = setPosition(positionMs)

    override fun setShuffle(enabled: Boolean) = mutable.update { it.copy(shuffle = enabled) }

    override fun setRepeatMode(mode: RepeatMode) = mutable.update { it.copy(repeatMode = mode) }

    private fun PlaybackState.at(index: Int): PlaybackState {
        val i = index.coerceIn(-1, queue.lastIndex)
        val track = queue.getOrNull(i)
        return copy(currentIndex = i, currentTrack = track, durationMs = track?.durationMs ?: 0)
    }
}
