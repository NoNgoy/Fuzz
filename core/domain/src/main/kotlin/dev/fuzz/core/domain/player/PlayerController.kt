package dev.fuzz.core.domain.player

import dev.fuzz.core.model.PlaybackState
import dev.fuzz.core.model.RepeatMode
import dev.fuzz.core.model.Track
import kotlinx.coroutines.flow.StateFlow

/**
 * Управление плеером для экранов. Реализация в core:player работает через MediaController,
 * как Android Auto и часы, поэтому состояние у всех точек управления одно.
 */
interface PlayerController {
    val state: StateFlow<PlaybackState>

    /** Заменить очередь и начать с [startIndex]. */
    fun playQueue(
        tracks: List<Track>,
        startIndex: Int = 0,
        startPositionMs: Long = 0,
    )

    fun playNext(tracks: List<Track>)

    fun addToQueue(tracks: List<Track>)

    fun moveInQueue(
        from: Int,
        to: Int,
    )

    fun removeFromQueue(index: Int)

    fun skipToQueueItem(index: Int)

    fun togglePlayPause()

    /** Пауза без переключения: ничего не делает, если плеер уже стоит. */
    fun pause()

    fun skipToNext()

    fun skipToPrevious()

    fun seekTo(positionMs: Long)

    fun setShuffle(enabled: Boolean)

    fun setRepeatMode(mode: RepeatMode)
}
