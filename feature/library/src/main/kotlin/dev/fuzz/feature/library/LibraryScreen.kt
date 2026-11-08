package dev.fuzz.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fuzz.core.design.theme.Spacing
import dev.fuzz.core.ui.component.TrackRow

@Composable
internal fun LibraryEntry(viewModel: LibraryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LibraryScreen(state = state, onIntent = viewModel::onIntent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryScreen(
    state: LibraryState,
    onIntent: (LibraryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.feature_library_title)) }) },
        floatingActionButton = {
            if (state.tracks.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { onIntent(LibraryIntent.ShuffleAll) },
                    icon = { Icon(Icons.Rounded.Shuffle, contentDescription = null) },
                    text = { Text(stringResource(R.string.feature_library_shuffle_all)) },
                )
            }
        },
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.tracks.isEmpty() -> {
                EmptyLibrary(Modifier.padding(padding))
            }

            else -> {
                LazyColumn(contentPadding = padding) {
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
}

@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.feature_library_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.feature_library_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}
