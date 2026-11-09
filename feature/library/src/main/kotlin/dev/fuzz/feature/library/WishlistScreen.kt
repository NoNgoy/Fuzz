package dev.fuzz.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fuzz.core.design.component.ScreenTitle
import dev.fuzz.core.design.theme.Spacing
import dev.fuzz.core.domain.wishlist.Wish
import dev.fuzz.core.domain.wishlist.WishReason
import dev.fuzz.core.ui.component.TrackRow
import dev.fuzz.core.ui.mvi.CollectEffects

@Composable
internal fun WishlistEntry(
    onBack: () -> Unit,
    viewModel: WishlistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val taken = stringResource(R.string.feature_library_wish_taken)
    val nothing = stringResource(R.string.feature_library_wish_nothing)
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is WishlistEffect.Taken -> snackbar.showSnackbar(taken.format(effect.title))
            WishlistEffect.NothingFound -> snackbar.showSnackbar(nothing)
        }
    }
    Box(Modifier.fillMaxSize()) {
        WishlistScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WishlistScreen(
    state: WishlistState,
    onIntent: (WishlistIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = Spacing.xs)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.feature_library_back))
            }
        }
        item {
            ScreenTitle(
                stringResource(R.string.feature_library_wishlist),
                Modifier.padding(horizontal = Spacing.screen),
            )
        }
        item {
            Text(
                stringResource(
                    if (state.wishes.isEmpty() && !state.isLoading) {
                        R.string.feature_library_wishlist_empty
                    } else {
                        R.string.feature_library_wishlist_hint
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screen),
            )
        }
        items(state.wishes, key = { it.ids.first() }) { wish ->
            WishRow(wish, searching = state.searching && state.picking == wish, onIntent = onIntent)
        }
    }
    val picking = state.picking
    if (picking != null && state.options.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = { onIntent(WishlistIntent.ClosePicker) }) {
            Text(
                stringResource(R.string.feature_library_wish_options),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s),
            )
            LazyColumn(Modifier.navigationBarsPadding().padding(bottom = Spacing.l)) {
                items(state.options, key = { it.id.value }) { track ->
                    TrackRow(track = track, onClick = { onIntent(WishlistIntent.Take(track)) })
                }
            }
        }
    }
}

@Composable
private fun WishRow(
    wish: Wish,
    searching: Boolean,
    onIntent: (WishlistIntent) -> Unit,
) {
    val links = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    wish.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    wish.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    wish.playlists.firstOrNull()?.let { stringResource(R.string.feature_library_wish_waits, it) }
                        ?: stringResource(reasonText(wish.reason)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = { onIntent(WishlistIntent.Remove(wish)) }) {
                Icon(
                    Icons.Rounded.Close,
                    stringResource(R.string.feature_library_wish_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            AssistChip(
                onClick = { onIntent(WishlistIntent.Find(wish)) },
                label = {
                    Text(stringResource(findLabel(searching)))
                },
                leadingIcon = {
                    if (searching) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                },
            )
            AssistChip(onClick = { links.openUri(wish.bandcampUrl) }, label = { Text(BANDCAMP) })
            AssistChip(onClick = { links.openUri(wish.qobuzUrl) }, label = { Text(QOBUZ) })
        }
    }
}

private fun findLabel(searching: Boolean): Int =
    if (searching) R.string.feature_library_wish_searching else R.string.feature_library_wish_find

private fun reasonText(reason: WishReason): Int =
    when (reason) {
        WishReason.Import -> R.string.feature_library_wish_import
        WishReason.Backup -> R.string.feature_library_wish_backup
        WishReason.Preview -> R.string.feature_library_wish_preview
    }

private const val BANDCAMP = "Bandcamp"
private const val QOBUZ = "Qobuz"
