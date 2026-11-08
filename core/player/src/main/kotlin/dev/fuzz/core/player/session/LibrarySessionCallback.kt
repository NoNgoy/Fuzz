package dev.fuzz.core.player.session

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.fuzz.core.domain.library.LibraryRepository
import dev.fuzz.core.domain.reaction.ToggleReactionUseCase
import dev.fuzz.core.domain.wave.WavePlayer
import dev.fuzz.core.domain.wave.WaveStartResult
import dev.fuzz.core.model.Reaction
import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId
import dev.fuzz.core.model.wave.WaveSeed
import dev.fuzz.core.player.queue.PlaybackSnapshotStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.future

/**
 * Дерево для Android Auto и часов, голосовой поиск, кнопки лайка и дизлайка, восстановление URI
 * из MediaController и возобновление воспроизведения после перезапуска.
 */
@OptIn(UnstableApi::class)
internal class LibrarySessionCallback(
    private val catalog: BrowseCatalog,
    private val library: LibraryRepository,
    private val wave: WavePlayer,
    private val toggleReaction: ToggleReactionUseCase,
    private val buttons: ReactionButtons,
    private val snapshots: PlaybackSnapshotStore,
    private val scope: CoroutineScope,
) : MediaLibrarySession.Callback {
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val defaults =
            if (controller.isTrusted) {
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
            } else {
                MediaSession.ConnectionResult.DEFAULT_UNTRUSTED_SESSION_AND_LIBRARY_COMMANDS
            }
        val commands =
            defaults
                .buildUpon()
                .add(ReactionButtons.LIKE)
                .add(ReactionButtons.DISLIKE)
                .build()
        return MediaSession.ConnectionResult
            .AcceptedResultBuilder(session, controller)
            .setAvailableSessionCommands(commands)
            .setMediaButtonPreferences(buttons.current)
            .build()
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        val reaction =
            when (customCommand.customAction) {
                ReactionButtons.LIKE.customAction -> Reaction.Like
                ReactionButtons.DISLIKE.customAction -> Reaction.Dislike
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
        val trackId =
            session.player.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() }
                ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_INVALID_STATE))
        return scope.future {
            // Дизлайк волна обработает сама: уберёт трек и переключит на следующий.
            toggleReaction(TrackId(trackId), reaction)
            SessionResult(SessionResult.RESULT_SUCCESS)
        }
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(LibraryResult.ofItem(MediaItemMapper.toMediaItem(catalog.root), params))

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        scope.future {
            val children = catalog.children(parentId).page(page, pageSize).map(MediaItemMapper::toMediaItem)
            LibraryResult.ofItemList(ImmutableList.copyOf(children), params)
        }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> =
        scope.future {
            val track = catalog.track(mediaId)
            if (track != null) {
                LibraryResult.ofItem(MediaItemMapper.toMediaItem(track), null)
            } else {
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            }
        }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> =
        scope.future {
            session.notifySearchResultChanged(browser, query, catalog.searchTracks(query).size, params)
            LibraryResult.ofVoid()
        }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        scope.future {
            val found = catalog.searchTracks(query).page(page, pageSize).map(MediaItemMapper::toMediaItem)
            LibraryResult.ofItemList(ImmutableList.copyOf(found), params)
        }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaItemsWithStartPosition> {
        val single = mediaItems.singleOrNull()
        val query = single?.requestMetadata?.searchQuery
        val fromCatalog =
            single != null && (BrowseCatalog.isCatalogId(single.mediaId) || (single.mediaId.isEmpty() && query != null))
        // Экраны приложения присылают готовую очередь с URI: её не трогаем.
        if (single == null || !fromCatalog) {
            return scope.future {
                MediaItemsWithStartPosition(resolve(mediaItems), startIndex, startPositionMs)
            }
        }
        return scope.future {
            val plan =
                if (query != null &&
                    single.mediaId.isEmpty()
                ) {
                    catalog.planForQuery(query)
                } else {
                    catalog.plan(single.mediaId)
                }
            when (plan) {
                PlayPlan.Wave -> startWave()
                is PlayPlan.Queue -> queue(plan.tracks, plan.startIndex)
                PlayPlan.Nothing -> MediaItemsWithStartPosition(emptyList(), 0, 0)
            }
        }
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<List<MediaItem>> = scope.future { resolve(mediaItems) }

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean,
    ): ListenableFuture<MediaItemsWithStartPosition> =
        scope.future {
            val snapshot = snapshots.load()
            val tracks = snapshot?.let { library.getTracks(it.ids) }.orEmpty()
            if (snapshot == null || tracks.isEmpty()) {
                MediaItemsWithStartPosition(emptyList(), 0, 0)
            } else {
                MediaItemsWithStartPosition(
                    tracks.map(MediaItemMapper::toMediaItem),
                    snapshot.currentIndex.coerceIn(0, tracks.lastIndex),
                    snapshot.positionMs,
                )
            }
        }

    /** Возвращает URI; Android Auto присылает только mediaId, недостающее берём из библиотеки. */
    private suspend fun resolve(mediaItems: List<MediaItem>): List<MediaItem> {
        val restored = mediaItems.map(MediaItemMapper::withRestoredUri)
        if (restored.all { it.localConfiguration != null }) return restored
        return restored.mapNotNull { item ->
            if (item.localConfiguration !=
                null
            ) {
                item
            } else {
                catalog.track(item.mediaId)?.let(MediaItemMapper::toMediaItem)
            }
        }
    }

    /** Волна отдаёт первую пачку сюда, а не в плеер: очередь машине возвращает сессия. */
    private suspend fun startWave(): MediaItemsWithStartPosition {
        val first = CompletableDeferred<List<Track>>()
        val result = wave.startWith(WaveSeed.MyWave) { first.complete(it) }
        val tracks = if (result == WaveStartResult.Started) first.await() else emptyList()
        return queue(tracks, 0)
    }

    private fun queue(
        tracks: List<Track>,
        startIndex: Int,
    ) = MediaItemsWithStartPosition(tracks.map(MediaItemMapper::toMediaItem), startIndex, C.TIME_UNSET)

    private fun <T> List<T>.page(
        page: Int,
        pageSize: Int,
    ): List<T> = if (pageSize <= 0) this else asSequence().drop(page * pageSize).take(pageSize).toList()
}
