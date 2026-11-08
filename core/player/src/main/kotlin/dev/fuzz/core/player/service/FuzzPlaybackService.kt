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
import dev.fuzz.core.domain.playlist.PlaylistRepository
import dev.fuzz.core.domain.reaction.ReactionRepository
import dev.fuzz.core.domain.reaction.ToggleReactionUseCase
import dev.fuzz.core.domain.search.SearchLibraryUseCase
import dev.fuzz.core.domain.wave.WavePlayer
import dev.fuzz.core.model.Reaction
import dev.fuzz.core.model.TrackId
import dev.fuzz.core.player.FuzzPlayer
import dev.fuzz.core.player.R
import dev.fuzz.core.player.queue.PlaybackSnapshot
import dev.fuzz.core.player.queue.PlaybackSnapshotStore
import dev.fuzz.core.player.session.BrowseCatalog
import dev.fuzz.core.player.session.BrowseTitles
import dev.fuzz.core.player.session.LibrarySessionCallback
import dev.fuzz.core.player.session.ReactionButtons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

    @Inject lateinit var playlists: PlaylistRepository

    @Inject lateinit var reactions: ReactionRepository

    @Inject lateinit var search: SearchLibraryUseCase

    @Inject lateinit var toggleReaction: ToggleReactionUseCase

    @Inject lateinit var wave: WavePlayer

    @Inject lateinit var snapshots: PlaybackSnapshotStore

    /** id текущего трека: по нему кнопка «Нравится» показывает, лайкнут ли он. */
    private val currentTrackId = MutableStateFlow<String?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaLibrarySession? = null
    private var player: FuzzPlayer? = null
    private var positionSaver: Job? = null

    override fun onCreate() {
        super.onCreate()
        val player = PlayerFactory.create(this).also { player = it }
        player.addListener(SnapshotListener())
        val buttons = ReactionButtons(this)
        val catalog =
            BrowseCatalog(
                BrowseTitles(
                    root = getString(R.string.player_browse_root),
                    wave = getString(R.string.player_browse_wave),
                    liked = getString(R.string.player_browse_liked),
                    playlists = getString(R.string.player_browse_playlists),
                    allTracks = getString(R.string.player_browse_all_tracks),
                ),
                library,
                playlists,
                reactions,
                search,
            )
        val callback = LibrarySessionCallback(catalog, library, wave, toggleReaction, buttons, snapshots, scope)
        val session =
            MediaLibrarySession
                .Builder(this, player, callback)
                .apply { sessionActivity()?.let(::setSessionActivity) }
                .setMediaButtonPreferences(buttons.current)
                .build()
                .also { this.session = it }
        followReaction(session, buttons)
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_fuzz_notification)
            },
        )
    }

    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    private fun followReaction(
        session: MediaLibrarySession,
        buttons: ReactionButtons,
    ) {
        scope.launch {
            currentTrackId
                .flatMapLatest { id -> if (id == null) flowOf(Reaction.None) else reactions.observe(TrackId(id)) }
                .collect { reaction ->
                    if (buttons.update(reaction)) session.setMediaButtonPreferences(buttons.current)
                }
        }
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
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                currentTrackId.value = player.currentMediaItem?.mediaId
            }
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
