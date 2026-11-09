package dev.fuzz.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fuzz.core.design.component.FuzzChip
import dev.fuzz.core.design.component.ScreenTitle
import dev.fuzz.core.design.component.SectionHeader
import dev.fuzz.core.design.theme.FuzzTheme
import dev.fuzz.core.design.theme.Spacing
import dev.fuzz.core.model.Playlist
import dev.fuzz.core.model.PlaylistId
import dev.fuzz.core.model.Track
import dev.fuzz.core.ui.component.NameDialog
import dev.fuzz.core.ui.component.TrackMenuHost
import dev.fuzz.core.ui.component.TrackRow
import dev.fuzz.core.ui.component.rememberTrackMenu
import dev.fuzz.core.ui.mvi.CollectEffects
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch

@Composable
internal fun LibraryEntry(
    onOpenTrackList: (TrackListRoute) -> Unit,
    onOpenLiked: () -> Unit,
    onOpenPlaylist: (PlaylistId) -> Unit,
    onOpenDownloaded: () -> Unit,
    onOpenFolders: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val addedTo = stringResource(R.string.feature_library_added_to)
    val alreadyIn = stringResource(R.string.feature_library_already_in)
    val hidden = stringResource(R.string.feature_library_hidden)
    val undo = stringResource(R.string.feature_library_undo)
    val deletedText = stringResource(R.string.feature_library_deleted_from_device)
    val deleteFailed = stringResource(R.string.feature_library_delete_failed)
    val scope = rememberCoroutineScope()
    val deleteFromDevice =
        rememberDeleteFromDevice(
            onDeleted = { viewModel.onIntent(LibraryIntent.Deleted(it)) },
            onFailed = { scope.launch { snackbar.showSnackbar(deleteFailed) } },
        )
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is LibraryEffect.OpenPlaylist -> {
                onOpenPlaylist(effect.id)
            }

            is LibraryEffect.AddedToPlaylist -> {
                snackbar.showSnackbar((if (effect.added) addedTo else alreadyIn).format(effect.playlistName))
            }

            is LibraryEffect.Hidden -> {
                val result = snackbar.showSnackbar(hidden.format(effect.title), actionLabel = undo)
                if (result == SnackbarResult.ActionPerformed) viewModel.onIntent(LibraryIntent.Restore(effect.track))
            }

            is LibraryEffect.DeletedFromDevice -> {
                snackbar.showSnackbar(deletedText.format(effect.title))
            }
        }
    }
    val menu = rememberTrackMenu()
    Box {
        LibraryScreen(
            state = state,
            onIntent = viewModel::onIntent,
            onOpenLiked = onOpenLiked,
            onOpenPlaylist = onOpenPlaylist,
            onOpenDownloaded = onOpenDownloaded,
            onOpenFolders = onOpenFolders,
            onOpenTrackList = onOpenTrackList,
            onMore = menu::open,
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    TrackMenuHost(
        state = menu,
        onHide = { viewModel.onIntent(LibraryIntent.Hide(it.id)) },
        onDeleteFromDevice = deleteFromDevice,
    )
}

private enum class LibraryFilter(
    val label: Int,
) {
    All(R.string.feature_library_filter_all),
    Playlists(R.string.feature_library_filter_playlists),
    Albums(R.string.feature_library_filter_albums),
    Artists(R.string.feature_library_filter_artists),
    Downloaded(R.string.feature_library_filter_downloaded),
}

@Composable
internal fun LibraryScreen(
    state: LibraryState,
    onIntent: (LibraryIntent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenLiked: () -> Unit = {},
    onOpenPlaylist: (PlaylistId) -> Unit = {},
    onOpenDownloaded: () -> Unit = {},
    onOpenFolders: () -> Unit = {},
    onOpenTrackList: (TrackListRoute) -> Unit = {},
    onMore: (Track) -> Unit = {},
) {
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.All) }
    var creating by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.statusBarsPadding(),
        contentPadding = PaddingValues(vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScreenTitle(stringResource(R.string.feature_library_title), Modifier.weight(1f))
                if (state.tracks.isNotEmpty()) {
                    IconButton(onClick = { onIntent(LibraryIntent.ShuffleAll) }) {
                        Icon(
                            Icons.Rounded.Shuffle,
                            contentDescription = stringResource(R.string.feature_library_shuffle_all),
                        )
                    }
                }
            }
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.s),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                items(LibraryFilter.entries) { f ->
                    FuzzChip(text = stringResource(f.label), selected = f == filter, onClick = { filter = f })
                }
            }
        }
        if (filter == LibraryFilter.All) {
            item {
                QuickEntry(
                    icon = Icons.Rounded.Favorite,
                    title = stringResource(R.string.feature_library_liked),
                    subtitle = tracksCount(state.likedCount),
                    container = FuzzTheme.colors.like,
                    content = Color.White,
                    onClick = onOpenLiked,
                )
            }
            item {
                QuickEntry(
                    icon = Icons.Rounded.Download,
                    title = stringResource(R.string.feature_library_downloaded),
                    subtitle = tracksCount(state.tracks.size),
                    onClick = onOpenDownloaded,
                )
            }
            item {
                QuickEntry(
                    icon = Icons.Outlined.Folder,
                    title = stringResource(R.string.feature_library_folders),
                    subtitle = stringResource(R.string.feature_library_folders_hint),
                    onClick = onOpenFolders,
                )
            }
        }
        when {
            state.isLoading -> {
                item { Loading() }
            }

            filter == LibraryFilter.Albums -> {
                albumSection(state.tracks, onOpenTrackList)
            }

            filter == LibraryFilter.Artists -> {
                artistSection(state.tracks, onOpenTrackList)
            }

            filter == LibraryFilter.Playlists -> {
                playlistSection(state.playlists, onCreate = { creating = true }, onOpen = onOpenPlaylist)
            }

            state.tracks.isEmpty() -> {
                item { EmptyLibrary() }
            }

            else -> {
                if (filter == LibraryFilter.All) {
                    playlistSection(state.playlists, onCreate = { creating = true }, onOpen = onOpenPlaylist)
                }
                item { SectionHeader(stringResource(R.string.feature_library_all_tracks)) }
                itemsIndexed(state.tracks, key = { _, track -> track.id.value }) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = track.id == state.currentTrackId,
                        onClick = { onIntent(LibraryIntent.PlayTrack(index)) },
                        trailing = { TrackMenuButton { onMore(track) } },
                    )
                }
            }
        }
    }
    if (creating) {
        NameDialog(
            title = stringResource(R.string.feature_library_new_playlist),
            initial = "",
            onConfirm = {
                creating = false
                onIntent(LibraryIntent.CreatePlaylist(it))
            },
            onDismiss = { creating = false },
        )
    }
}

private fun LazyListScope.playlistSection(
    playlists: ImmutableList<Playlist>,
    onCreate: () -> Unit,
    onOpen: (PlaylistId) -> Unit,
) {
    item {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(stringResource(R.string.feature_library_playlists), Modifier.weight(1f))
            IconButton(onClick = onCreate) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.feature_library_new_playlist))
            }
        }
    }
    if (playlists.isEmpty()) {
        item { Hint(stringResource(R.string.feature_library_no_playlists)) }
    }
    items(playlists, key = { "playlist:" + it.id.value }) { playlist ->
        QuickEntry(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            title = playlist.name,
            subtitle = tracksCount(playlist.trackCount),
            onClick = { onOpen(playlist.id) },
        )
    }
}

/** Альбом в коллекции: одноимённые альбомы разных артистов не склеиваются. */
private data class AlbumGroup(
    val title: String,
    val artist: String,
    val count: Int,
)

private fun albumsOf(tracks: List<Track>): List<AlbumGroup> =
    tracks
        .filter { !it.albumTitle.isNullOrBlank() }
        .groupBy { it.albumTitle.orEmpty() to it.artistName }
        .map { (key, list) -> AlbumGroup(key.first, key.second, list.size) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

private fun artistsOf(tracks: List<Track>): List<Pair<String, Int>> =
    tracks
        .groupingBy { it.artistName }
        .eachCount()
        .toList()
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.first })

private fun LazyListScope.albumSection(
    tracks: ImmutableList<Track>,
    onOpen: (TrackListRoute) -> Unit,
) {
    val albums = albumsOf(tracks)
    if (albums.isEmpty()) {
        item { Hint(stringResource(R.string.feature_library_no_albums)) }
    }
    items(albums, key = { "album:" + it.title + "\u0000" + it.artist }) { album ->
        QuickEntry(
            icon = Icons.Rounded.Album,
            title = album.title,
            subtitle = album.artist + " · " + tracksCount(album.count),
            onClick = { onOpen(TrackListRoute(album = album.title, artist = album.artist)) },
        )
    }
}

private fun LazyListScope.artistSection(
    tracks: ImmutableList<Track>,
    onOpen: (TrackListRoute) -> Unit,
) {
    val artists = artistsOf(tracks)
    if (artists.isEmpty()) {
        item { Hint(stringResource(R.string.feature_library_empty_body)) }
    }
    items(artists, key = { "artist:" + it.first }) { (name, count) ->
        QuickEntry(
            icon = Icons.Rounded.Person,
            title = name,
            subtitle = tracksCount(count),
            onClick = { onOpen(TrackListRoute(artist = name)) },
        )
    }
}

@Composable
internal fun TrackMenuButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.Rounded.MoreVert,
            contentDescription = stringResource(dev.fuzz.core.ui.R.string.core_ui_track_menu),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun tracksCount(count: Int) = pluralStringResource(R.plurals.feature_library_tracks, count, count)

@Composable
private fun QuickEntry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = Spacing.screen, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(
            Modifier.size(44.dp).background(container, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = content)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(Spacing.xxl), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.l),
    )
}

@Composable
private fun EmptyLibrary() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(stringResource(R.string.feature_library_empty_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.feature_library_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
