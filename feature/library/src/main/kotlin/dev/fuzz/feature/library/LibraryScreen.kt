package dev.fuzz.feature.library

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import dev.fuzz.core.ui.component.TrackRow

@Composable
internal fun LibraryEntry(viewModel: LibraryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LibraryScreen(state = state, onIntent = viewModel::onIntent)
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
) {
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.All) }
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
                )
            }
            item {
                QuickEntry(
                    icon = Icons.Rounded.Download,
                    title = stringResource(R.string.feature_library_downloaded),
                    subtitle = tracksCount(state.tracks.size),
                )
            }
            item {
                QuickEntry(
                    icon = Icons.Outlined.Folder,
                    title = stringResource(R.string.feature_library_folders),
                    subtitle = stringResource(R.string.feature_library_folders_hint),
                )
            }
            item {
                QuickEntry(
                    icon = Icons.Outlined.ShoppingBag,
                    title = stringResource(R.string.feature_library_wishlist),
                    subtitle = stringResource(R.string.feature_library_wishlist_hint),
                )
            }
        }
        when {
            state.isLoading -> {
                item { Loading() }
            }

            filter == LibraryFilter.Playlists || filter == LibraryFilter.Albums || filter == LibraryFilter.Artists -> {
                item { Hint(stringResource(R.string.feature_library_stage3)) }
            }

            state.tracks.isEmpty() -> {
                item { EmptyLibrary() }
            }

            else -> {
                item { SectionHeader(stringResource(R.string.feature_library_all_tracks)) }
                itemsIndexed(state.tracks, key = { _, track -> track.id.value }) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = track.id == state.currentTrackId,
                        onClick = { onIntent(LibraryIntent.PlayTrack(index)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun tracksCount(count: Int) = pluralStringResource(R.plurals.feature_library_tracks, count, count)

@Composable
private fun QuickEntry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.s),
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
