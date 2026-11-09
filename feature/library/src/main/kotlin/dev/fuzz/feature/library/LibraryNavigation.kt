package dev.fuzz.feature.library

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

/**
 * Список треков: без параметров это «Мне нравится», с [downloaded] — все треки на телефоне,
 * с [album] — альбом (вместе с [artist], чтобы не смешать одноимённые), с одним [artist] — все треки артиста.
 */
@Serializable
data class TrackListRoute(
    val playlistId: String? = null,
    val downloaded: Boolean = false,
    val album: String? = null,
    val artist: String? = null,
)

fun NavGraphBuilder.libraryScreen(
    onOpenLiked: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenDownloaded: () -> Unit,
    onOpenFolders: () -> Unit,
    onOpenTrackList: (TrackListRoute) -> Unit,
) {
    composable<LibraryRoute> {
        LibraryEntry(
            onOpenTrackList = onOpenTrackList,
            onOpenLiked = onOpenLiked,
            onOpenPlaylist = { onOpenPlaylist(it.value) },
            onOpenDownloaded = onOpenDownloaded,
            onOpenFolders = onOpenFolders,
        )
    }
}

fun NavGraphBuilder.trackListScreen(onBack: () -> Unit) {
    composable<TrackListRoute> { TrackListEntry(onBack = onBack) }
}
