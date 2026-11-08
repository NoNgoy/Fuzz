package dev.fuzz.feature.library

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

/** Список треков: без [playlistId] это «Мне нравится». */
@Serializable
data class TrackListRoute(
    val playlistId: String? = null,
)

fun NavGraphBuilder.libraryScreen(
    onOpenLiked: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    composable<LibraryRoute> {
        LibraryEntry(onOpenLiked = onOpenLiked, onOpenPlaylist = { onOpenPlaylist(it.value) })
    }
}

fun NavGraphBuilder.trackListScreen(onBack: () -> Unit) {
    composable<TrackListRoute> { TrackListEntry(onBack = onBack) }
}
