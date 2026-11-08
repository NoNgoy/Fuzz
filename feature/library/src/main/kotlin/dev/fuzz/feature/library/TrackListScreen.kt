package dev.fuzz.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fuzz.core.design.component.ScreenTitle
import dev.fuzz.core.design.theme.Spacing
import dev.fuzz.core.model.Track
import dev.fuzz.core.ui.component.NameDialog
import dev.fuzz.core.ui.component.TrackRow
import dev.fuzz.core.ui.mvi.CollectEffects

@Composable
internal fun TrackListEntry(
    onBack: () -> Unit,
    viewModel: TrackListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            TrackListEffect.Closed -> onBack()
        }
    }
    TrackListScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}

@Composable
internal fun TrackListScreen(
    state: TrackListState,
    onIntent: (TrackListIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPlaylist = state.kind is TrackListKind.Playlist
    val title = state.title ?: if (isPlaylist) "" else stringResource(R.string.feature_library_liked)
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = Spacing.l),
    ) {
        item {
            TopBar(
                isPlaylist = isPlaylist,
                onBack = onBack,
                onRename = { renaming = true },
                onDelete = { deleting = true },
            )
        }
        item {
            Header(title = title, count = state.tracks.size, canPlay = state.tracks.isNotEmpty(), onIntent = onIntent)
        }
        when {
            state.isLoading -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(Spacing.xxl), Alignment.Center) { CircularProgressIndicator() }
                }
            }

            state.tracks.isEmpty() -> {
                item {
                    Text(
                        stringResource(
                            if (isPlaylist) {
                                R.string.feature_library_playlist_empty
                            } else {
                                R.string.feature_library_liked_empty
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.l),
                    )
                }
            }

            else -> {
                itemsIndexed(state.tracks, key = { _, track -> track.id.value }) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = track.id == state.currentTrackId,
                        onClick = { onIntent(TrackListIntent.Play(index)) },
                        trailing = { RemoveMenu(isPlaylist, track, onIntent) },
                    )
                }
            }
        }
    }
    if (renaming) {
        NameDialog(
            title = stringResource(R.string.feature_library_rename),
            initial = title,
            onConfirm = {
                renaming = false
                onIntent(TrackListIntent.Rename(it))
            },
            onDismiss = { renaming = false },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            text = { Text(stringResource(R.string.feature_library_delete_confirm, title)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    onIntent(TrackListIntent.Delete)
                }) { Text(stringResource(R.string.feature_library_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }) {
                    Text(stringResource(dev.fuzz.core.ui.R.string.core_ui_cancel))
                }
            },
        )
    }
}

@Composable
private fun TopBar(
    isPlaylist: Boolean,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.feature_library_back),
            )
        }
        Spacer(Modifier.weight(1f))
        if (isPlaylist) {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.feature_library_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.feature_library_rename)) },
                        onClick = {
                            menu = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.feature_library_delete)) },
                        onClick = {
                            menu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(
    title: String,
    count: Int,
    canPlay: Boolean,
    onIntent: (TrackListIntent) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        ScreenTitle(title)
        Text(
            tracksCount(count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Button(onClick = { onIntent(TrackListIntent.Play(0)) }, enabled = canPlay) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.feature_library_play), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FilledTonalButton(onClick = { onIntent(TrackListIntent.Shuffle) }, enabled = canPlay) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.feature_library_shuffle), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RemoveMenu(
    isPlaylist: Boolean,
    track: Track,
    onIntent: (TrackListIntent) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        TrackMenuButton { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (isPlaylist) {
                                R.string.feature_library_remove_from_playlist
                            } else {
                                R.string.feature_library_remove_like
                            },
                        ),
                    )
                },
                onClick = {
                    open = false
                    onIntent(TrackListIntent.Remove(track.id))
                },
            )
        }
    }
}
