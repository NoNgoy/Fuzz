package dev.fuzz.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fuzz.core.design.component.ScreenTitle
import dev.fuzz.core.design.component.SectionHeader
import dev.fuzz.core.design.theme.FuzzTheme
import dev.fuzz.core.design.theme.Spacing
import dev.fuzz.core.domain.artist.AlbumProfile
import dev.fuzz.core.domain.artist.ArtistProfile
import dev.fuzz.core.ui.component.Artwork
import dev.fuzz.core.ui.component.TrackRow
import dev.fuzz.core.ui.mvi.CollectEffects
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun ArtistEntry(
    onBack: () -> Unit,
    onOpenArtist: (ArtistRoute) -> Unit,
    onOpenAlbum: (AlbumRoute) -> Unit,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val notPlayable = stringResource(R.string.feature_search_not_playable)
    CollectEffects(viewModel.effects) { snackbar.showSnackbar(notPlayable) }
    Box(Modifier.fillMaxSize()) {
        ArtistScreen(
            state = state,
            onIntent = viewModel::onIntent,
            onBack = onBack,
            onOpenArtist = { onOpenArtist(it.route()) },
            onOpenAlbum = { onOpenAlbum(it.route()) },
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
internal fun ArtistScreen(
    state: ArtistState,
    onIntent: (ArtistIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenArtist: (ArtistProfile) -> Unit = {},
    onOpenAlbum: (AlbumProfile) -> Unit = {},
) {
    val listState = rememberLazyListState()
    LoadMoreWhenNearEnd(listState, state.tracks.size, state.tracksHasMore) { onIntent(ArtistIntent.LoadMoreTracks) }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        item { BackBar(onBack) }
        item { ArtistHeader(state, onIntent) }
        if (state.isLoading) item { Spinner() }
        if (state.popular.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.feature_search_popular)) }
            itemsIndexed(state.popular, key = { _, t -> "popular:" + t.id.value }) { index, track ->
                TrackRow(track = track, onClick = { onIntent(ArtistIntent.PlayPopular(index)) })
            }
        }
        if (state.albums.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.feature_search_albums)) }
            item { AlbumCarousel(state.albums, onOpenAlbum) }
        }
        if (state.inCollection.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.feature_search_in_collection)) }
            itemsIndexed(state.inCollection, key = { _, t -> "mine:" + t.id.value }) { index, track ->
                TrackRow(track = track, onClick = { onIntent(ArtistIntent.PlayCollection(index)) })
            }
        }
        if (state.related.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.feature_search_related)) }
            item { ArtistCarousel(state.related.map { ArtistCardData(it, null) }, onOpenArtist) }
        }
        allTracks(state, onIntent)
    }
}

private fun LazyListScope.allTracks(
    state: ArtistState,
    onIntent: (ArtistIntent) -> Unit,
) {
    item { SectionHeader(stringResource(R.string.feature_search_all_tracks)) }
    itemsIndexed(state.tracks, key = { _, t -> "all:" + t.id.value }) { index, track ->
        TrackRow(track = track, onClick = { onIntent(ArtistIntent.PlayTrack(index)) })
    }
    when {
        state.tracksLoading -> {
            item { Spinner() }
        }

        state.tracks.isEmpty() && !state.tracksHasMore -> {
            item {
                Hint(stringResource(R.string.feature_search_no_tracks))
            }
        }
    }
}

@Composable
private fun ArtistHeader(
    state: ArtistState,
    onIntent: (ArtistIntent) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        ArtistPhoto(state.artist.name, state.artist.pictureUrl, Modifier.size(PHOTO_DP.dp))
        ScreenTitle(state.artist.name)
        state.artist.fans?.takeIf { it > 0 }?.let { fans ->
            Text(
                pluralStringResource(R.plurals.feature_search_fans, fans, fans),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val canPlay = state.popular.isNotEmpty() || state.tracks.isNotEmpty() || state.inCollection.isNotEmpty()
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Button(
                onClick = {
                    when {
                        state.popular.isNotEmpty() -> onIntent(ArtistIntent.PlayPopular(0))
                        state.inCollection.isNotEmpty() -> onIntent(ArtistIntent.PlayCollection(0))
                        else -> onIntent(ArtistIntent.PlayTrack(0))
                    }
                },
                enabled = canPlay && !state.preparing,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.feature_search_listen))
            }
            FilledTonalButton(onClick = { onIntent(ArtistIntent.Shuffle) }, enabled = canPlay && !state.preparing) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.feature_search_shuffle))
            }
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

/** Карточка артиста в каруселях: фото (или первая буква) и подпись. */
internal data class ArtistCardData(
    val artist: ArtistProfile,
    val subtitle: String?,
)

@Composable
internal fun ArtistCarousel(
    artists: List<ArtistCardData>,
    onOpen: (ArtistProfile) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        items(artists, key = { it.artist.id ?: it.artist.name }) { card ->
            Column(
                Modifier.width(CARD_DP.dp).clickable { onOpen(card.artist) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                ArtistPhoto(card.artist.name, card.artist.pictureUrl, Modifier.size(CARD_DP.dp))
                Text(
                    card.artist.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                card.subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumCarousel(
    albums: ImmutableList<AlbumProfile>,
    onOpen: (AlbumProfile) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        items(albums, key = { it.id }) { album ->
            Column(
                Modifier.width(ALBUM_DP.dp).clickable { onOpen(album) },
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Artwork(album.coverUrl, Modifier.size(ALBUM_DP.dp), MaterialTheme.shapes.medium)
                Text(
                    album.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                album.year?.let {
                    Text(
                        it.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ArtistPhoto(
    name: String,
    url: String?,
    modifier: Modifier = Modifier,
) {
    if (url != null) {
        Artwork(url, modifier, CircleShape)
    } else {
        Box(modifier.background(FuzzTheme.colors.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
            Text(
                name.take(1).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
internal fun BackBar(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs)) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.feature_search_back),
            )
        }
    }
}

@Composable
internal fun Spinner() {
    Box(Modifier.fillMaxWidth().padding(Spacing.l), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

/** Когда до конца списка остаётся несколько строк, просит следующую страницу. */
@Composable
internal fun LoadMoreWhenNearEnd(
    listState: LazyListState,
    size: Int,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
) {
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_AHEAD
        }
    }
    LaunchedEffect(nearEnd, size, hasMore) { if (nearEnd && hasMore) onLoadMore() }
}

private const val PHOTO_DP = 180
private const val CARD_DP = 112
private const val ALBUM_DP = 140
private const val LOAD_AHEAD = 5
