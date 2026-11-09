package dev.fuzz.core.player.controller

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.fuzz.core.domain.player.PlayerController
import dev.fuzz.core.model.PlaybackState
import dev.fuzz.core.model.RepeatMode
import dev.fuzz.core.model.Track
import dev.fuzz.core.player.service.FuzzPlaybackService
import dev.fuzz.core.player.session.MediaItemMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** [PlayerController] поверх MediaController: экраны управляют плеером так же, как Android Auto и часы. */
@Singleton
internal class MediaControllerPlayerController
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : PlayerController {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val mutableState = MutableStateFlow(PlaybackState())
        override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

        private var ticker: Job? = null
        private var queueCache: List<Track> = emptyList()

        private val controller =
            scope.async {
                val token = SessionToken(context, ComponentName(context, FuzzPlaybackService::class.java))
                MediaController.Builder(context, token).buildAsync().await().also { c ->
                    c.addListener(Listener())
                    rebuildQueue(c)
                    publish(c)
                    updateTicker(c)
                }
            }

        private fun withController(block: (MediaController) -> Unit) {
            scope.launch { block(controller.await()) }
        }

        override fun playQueue(
            tracks: List<Track>,
            startIndex: Int,
            startPositionMs: Long,
        ) = withController { c ->
            c.setMediaItems(tracks.map(MediaItemMapper::toMediaItem), startIndex, startPositionMs)
            c.prepare()
            c.play()
        }

        override fun playNext(tracks: List<Track>) =
            withController { c ->
                val index = if (c.mediaItemCount == 0) 0 else c.currentMediaItemIndex + 1
                c.addMediaItems(index, tracks.map(MediaItemMapper::toMediaItem))
                if (c.playbackState == Player.STATE_IDLE) c.prepare()
            }

        override fun addToQueue(tracks: List<Track>) =
            withController { c ->
                c.addMediaItems(tracks.map(MediaItemMapper::toMediaItem))
                if (c.playbackState == Player.STATE_IDLE) c.prepare()
            }

        override fun moveInQueue(
            from: Int,
            to: Int,
        ) = withController { it.moveMediaItem(from, to) }

        override fun removeFromQueue(index: Int) = withController { it.removeMediaItem(index) }

        override fun skipToQueueItem(index: Int) =
            withController { c ->
                c.seekToDefaultPosition(index)
                c.play()
            }

        override fun pause() = withController { it.pause() }

        override fun togglePlayPause() =
            withController { c ->
                if (c.isPlaying) {
                    c.pause()
                } else {
                    if (c.playbackState == Player.STATE_IDLE) c.prepare()
                    if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
                    c.play()
                }
            }

        override fun skipToNext() = withController { it.seekToNext() }

        override fun skipToPrevious() = withController { it.seekToPrevious() }

        override fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

        override fun setShuffle(enabled: Boolean) = withController { it.shuffleModeEnabled = enabled }

        override fun setRepeatMode(mode: RepeatMode) =
            withController {
                it.repeatMode =
                    when (mode) {
                        RepeatMode.Off -> Player.REPEAT_MODE_OFF
                        RepeatMode.One -> Player.REPEAT_MODE_ONE
                        RepeatMode.All -> Player.REPEAT_MODE_ALL
                    }
            }

        private fun rebuildQueue(c: Player) {
            queueCache = (0 until c.mediaItemCount).map { MediaItemMapper.toTrack(c.getMediaItemAt(it)) }
        }

        private fun publish(c: Player) {
            mutableState.update {
                PlaybackState(
                    currentTrack = c.currentMediaItem?.let(MediaItemMapper::toTrack),
                    isPlaying = c.isPlaying,
                    isBuffering = c.playbackState == Player.STATE_BUFFERING,
                    positionMs = c.currentPosition.coerceAtLeast(0),
                    durationMs = c.duration.takeIf { d -> d > 0 } ?: 0,
                    queue = queueCache,
                    currentIndex = if (c.mediaItemCount == 0) -1 else c.currentMediaItemIndex,
                    shuffle = c.shuffleModeEnabled,
                    repeatMode =
                        when (c.repeatMode) {
                            Player.REPEAT_MODE_ONE -> RepeatMode.One
                            Player.REPEAT_MODE_ALL -> RepeatMode.All
                            else -> RepeatMode.Off
                        },
                )
            }
        }

        /** Пока музыка играет, позиция обновляется дважды в секунду. */
        private fun updateTicker(c: Player) {
            ticker?.cancel()
            if (!c.isPlaying) return
            ticker =
                scope.launch {
                    while (isActive) {
                        mutableState.update { it.copy(positionMs = c.currentPosition.coerceAtLeast(0)) }
                        delay(TICK_MS)
                    }
                }
        }

        private inner class Listener : Player.Listener {
            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) {
                if (events.contains(Player.EVENT_TIMELINE_CHANGED)) rebuildQueue(player)
                publish(player)
                if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) updateTicker(player)
            }
        }

        private companion object {
            const val TICK_MS = 500L
        }
    }
