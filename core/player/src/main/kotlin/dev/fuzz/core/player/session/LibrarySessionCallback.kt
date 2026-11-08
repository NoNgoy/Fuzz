package dev.fuzz.core.player.session

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import dev.fuzz.core.domain.library.LibraryRepository
import dev.fuzz.core.model.TrackId
import dev.fuzz.core.player.R
import dev.fuzz.core.player.queue.PlaybackSnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future

/**
 * Дерево для Android Auto и часов, восстановление URI из MediaController и возобновление
 * воспроизведения после перезапуска.
 */
@OptIn(UnstableApi::class)
internal class LibrarySessionCallback(
    private val context: Context,
    private val library: LibraryRepository,
    private val snapshots: PlaybackSnapshotStore,
    private val scope: CoroutineScope,
) : MediaLibrarySession.Callback {
    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> =
        scope.future {
            LibraryResult.ofItem(
                MediaItemMapper.browsable(BrowseTree.ROOT, context.getString(R.string.player_browse_root)),
                params,
            )
        }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        scope.future {
            val children =
                when (parentId) {
                    BrowseTree.ROOT -> {
                        listOf(
                            MediaItemMapper.browsable(
                                BrowseTree.ALL_TRACKS,
                                context.getString(R.string.player_browse_all_tracks),
                                MediaMetadata.MEDIA_TYPE_PLAYLIST,
                            ),
                        )
                    }

                    BrowseTree.ALL_TRACKS -> {
                        library
                            .observeTracks()
                            .first()
                            .asSequence()
                            .drop(page * pageSize)
                            .take(pageSize)
                            .map(MediaItemMapper::toMediaItem)
                            .toList()
                    }

                    else -> {
                        emptyList()
                    }
                }
            LibraryResult.ofItemList(ImmutableList.copyOf(children), params)
        }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> =
        scope.future {
            val track = library.getTracks(listOf(TrackId(mediaId))).firstOrNull()
            if (track != null) {
                LibraryResult.ofItem(MediaItemMapper.toMediaItem(track), null)
            } else {
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            }
        }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<List<MediaItem>> =
        scope.future {
            val restored = mediaItems.map(MediaItemMapper::withRestoredUri)
            // Android Auto присылает только mediaId: недостающее берём из библиотеки.
            val missing = restored.filter { it.localConfiguration == null }.map { TrackId(it.mediaId) }
            if (missing.isEmpty()) return@future restored
            val found = library.getTracks(missing).associateBy { it.id.value }
            restored.mapNotNull { item ->
                if (item.localConfiguration != null) item else found[item.mediaId]?.let(MediaItemMapper::toMediaItem)
            }
        }

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
}

internal object BrowseTree {
    const val ROOT = "fuzz:root"
    const val ALL_TRACKS = "fuzz:all_tracks"
}
