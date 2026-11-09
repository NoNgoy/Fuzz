package dev.fuzz.feature.library

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

@Serializable
data object WishlistRoute

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
    /** Имя [dev.fuzz.core.domain.library.SmartPlaylist]. */
    val smart: String? = null,
)

fun NavGraphBuilder.libraryScreen(
    onOpenLiked: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenDownloaded: () -> Unit,
    onOpenFolders: () -> Unit,
    onOpenTrackList: (TrackListRoute) -> Unit,
    onOpenWishlist: () -> Unit = {},
) {
    composable<LibraryRoute> {
        LibraryEntry(
            onOpenTrackList = onOpenTrackList,
            onOpenWishlist = onOpenWishlist,
            onOpenLiked = onOpenLiked,
            onOpenPlaylist = { onOpenPlaylist(it.value) },
            onOpenDownloaded = onOpenDownloaded,
            onOpenFolders = onOpenFolders,
        )
    }
}

fun NavGraphBuilder.wishlistScreen(onBack: () -> Unit) {
    composable<WishlistRoute> { WishlistEntry(onBack = onBack) }
}

fun NavGraphBuilder.trackListScreen(onBack: () -> Unit) {
    composable<TrackListRoute> { TrackListEntry(onBack = onBack) }
}
