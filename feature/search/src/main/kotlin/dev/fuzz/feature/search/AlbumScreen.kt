package dev.fuzz.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fuzz.core.design.component.ScreenTitle
import dev.fuzz.core.design.theme.Spacing
import dev.fuzz.core.ui.component.Artwork
import dev.fuzz.core.ui.component.TrackRow
import dev.fuzz.core.ui.mvi.CollectEffects

@Composable
internal fun AlbumEntry(
    onBack: () -> Unit,
    onOpenArtist: (ArtistRoute) -> Unit,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val notPlayable = stringResource(R.string.feature_search_not_playable)
    CollectEffects(viewModel.effects) { snackbar.showSnackbar(notPlayable) }
    Box(Modifier.fillMaxSize()) {
        AlbumScreen(
            state = state,
            onIntent = viewModel::onIntent,
            onBack = onBack,
            onOpenArtist = { onOpenArtist(ArtistRoute(name = state.album.artist)) },
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
internal fun AlbumScreen(
    state: AlbumState,
    onIntent: (AlbumIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenArtist: () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        item { BackBar(onBack) }
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Artwork(state.album.coverUrl, Modifier.size(COVER_DP.dp), MaterialTheme.shapes.large)
                ScreenTitle(state.album.title)
                Text(
                    listOfNotNull(state.album.artist, state.album.year?.toString()).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable(onClick = onOpenArtist),
                )
                Button(
                    onClick = { onIntent(AlbumIntent.Play(0)) },
                    enabled = state.tracks.isNotEmpty() && !state.preparing,
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.feature_search_listen))
                }
                if (state.preparing) {
                    Text(
                        stringResource(R.string.feature_search_preparing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (state.isLoading) item { Spinner() }
        itemsIndexed(state.tracks, key = { _, t -> t.id.value }) { index, track ->
            TrackRow(track = track, onClick = { onIntent(AlbumIntent.Play(index)) })
        }
    }
}

private const val COVER_DP = 220
