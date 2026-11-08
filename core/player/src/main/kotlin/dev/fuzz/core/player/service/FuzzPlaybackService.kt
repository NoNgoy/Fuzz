package dev.fuzz.core.player.service

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import dagger.hilt.android.AndroidEntryPoint
import dev.fuzz.core.domain.library.LibraryRepository
import dev.fuzz.core.player.FuzzPlayer
import dev.fuzz.core.player.R
import dev.fuzz.core.player.queue.PlaybackSnapshot
import dev.fuzz.core.player.queue.PlaybackSnapshotStore
import dev.fuzz.core.player.session.LibrarySessionCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Сервис воспроизведения. Через одну MediaLibrarySession получают плеер экраны приложения,
 * уведомление и экран блокировки, кнопки наушников, часы и Android Auto.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class FuzzPlaybackService : MediaLibraryService() {
    @Inject lateinit var library: LibraryRepository

    @Inject lateinit var snapshots: PlaybackSnapshotStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaLibrarySession? = null
    private var player: FuzzPlayer? = null
    private var positionSaver: Job? = null

    override fun onCreate() {
        super.onCreate()
        val player = PlayerFactory.create(this).also { player = it }
        player.addListener(SnapshotListener())
        session =
            MediaLibrarySession
                .Builder(this, player, LibrarySessionCallback(this, library, snapshots, scope))
                .apply { sessionActivity()?.let(::setSessionActivity) }
                .build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_fuzz_notification)
            },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        // Если музыка не играет, смахивание приложения завершает сервис; если играет, продолжаем в фоне.
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        player?.let { p -> scope.launch { withContext(NonCancellable) { saveSnapshot(p) } } }
        session?.run {
            player.release()
            release()
        }
        session = null
        player = null
        scope.cancel()
        super.onDestroy()
    }

    private fun sessionActivity(): PendingIntent? =
        packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

    private suspend fun saveSnapshot(player: Player) {
        if (player.mediaItemCount == 0) return
        snapshots.save(
            PlaybackSnapshot(
                trackIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId },
                currentIndex = player.currentMediaItemIndex,
                positionMs = player.currentPosition,
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
            ),
        )
    }

    /** Сохраняет очередь при её изменении и позицию раз в несколько секунд, пока играет музыка. */
    private inner class SnapshotListener : Player.Listener {
        override fun onEvents(
            player: Player,
            events: Player.Events,
        ) {
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                )
            ) {
                scope.launch { saveSnapshot(player) }
            }
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                positionSaver?.cancel()
                scope.launch { saveSnapshot(player) }
                if (player.isPlaying) {
                    positionSaver =
                        scope.launch {
                            while (isActive) {
                                delay(POSITION_SAVE_INTERVAL_MS)
                                saveSnapshot(player)
                            }
                        }
                }
            }
        }
    }

    private companion object {
        const val POSITION_SAVE_INTERVAL_MS = 5_000L
    }
}
