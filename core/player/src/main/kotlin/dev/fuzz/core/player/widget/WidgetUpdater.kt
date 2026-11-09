package dev.fuzz.core.player.widget

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/** Перерисовывает виджет, когда меняется трек или пауза. Обложку грузит загрузчик картинок сессии. */
@OptIn(UnstableApi::class)
internal class WidgetUpdater(
    private val context: Context,
    private val session: MediaSession,
    private val scope: CoroutineScope,
) : Player.Listener {
    private var artworkFor: MediaMetadata? = null
    private var artwork: Bitmap? = null
    private var loading: Job? = null

    init {
        session.player.addListener(this)
        render()
    }

    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        if (events.containsAny(
                Player.EVENT_MEDIA_METADATA_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
            )
        ) {
            render()
        }
    }

    fun release() {
        loading?.cancel()
        session.player.removeListener(this)
    }

    private fun render() {
        val player = session.player
        val metadata = player.mediaMetadata
        val state =
            WidgetState(
                title = metadata.title?.toString().orEmpty(),
                artist = metadata.artist?.toString().orEmpty(),
                playing = player.playWhenReady && player.playbackState != Player.STATE_ENDED,
                artwork = artwork.takeIf { artworkFor == metadata },
            )
        PlayerWidget.render(context, state)
        if (artworkFor != metadata) loadArtwork(metadata, state)
    }

    private fun loadArtwork(
        metadata: MediaMetadata,
        state: WidgetState,
    ) {
        loading?.cancel()
        artworkFor = metadata
        artwork = null
        val future = session.bitmapLoader.loadBitmapFromMetadata(metadata) ?: return
        loading =
            scope.launch {
                val bitmap =
                    try {
                        future.await()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception,
                    ) {
                        // Обложка не загрузилась (нет сети, битый файл): виджет остаётся со значком.
                        Log.w(TAG, "widget artwork failed", e)
                        null
                    }
                if (bitmap != null && artworkFor == metadata) {
                    artwork = bitmap
                    PlayerWidget.render(context, state.copy(artwork = bitmap))
                }
            }
    }

    private companion object {
        const val TAG = "FuzzWidget"
    }
}
