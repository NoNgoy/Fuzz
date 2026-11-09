package dev.fuzz.feature.library

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

/** Список треков: без [playlistId] это «Мне нравится», с [downloaded] — все треки на телефоне. */
@Serializable
data class TrackListRoute(
    val playlistId: String? = null,
    val downloaded: Boolean = false,
)

fun NavGraphBuilder.libraryScreen(
    onOpenLiked: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenDownloaded: () -> Unit,
    onOpenFolders: () -> Unit,
) {
    composable<LibraryRoute> {
        LibraryEntry(
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
