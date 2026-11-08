package dev.fuzz.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fuzz.core.design.component.FuzzChip
import dev.fuzz.core.design.component.GradientTile
import dev.fuzz.core.design.component.ScreenTitle
import dev.fuzz.core.design.component.SectionHeader
import dev.fuzz.core.design.theme.FuzzTheme
import dev.fuzz.core.design.theme.Spacing
import dev.fuzz.core.domain.search.ArtistHit
import dev.fuzz.core.ui.component.TrackRow

@Composable
internal fun SearchEntry(viewModel: SearchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchScreen(state = state, onIntent = viewModel::onIntent)
}

@Composable
internal fun SearchScreen(
    state: SearchState,
    onIntent: (SearchIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.statusBarsPadding(),
        contentPadding = PaddingValues(vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        if (state.showBrowse) {
            item {
                ScreenTitle(
                    stringResource(R.string.feature_search_title),
                    Modifier.padding(horizontal = Spacing.screen),
                )
            }
        }
        item {
            SearchField(
                query = state.query,
                onQueryChange = { onIntent(SearchIntent.QueryChanged(it)) },
                modifier = Modifier.padding(horizontal = Spacing.screen),
            )
        }
        item { SourceChips(state.source) { onIntent(SearchIntent.SourceSelected(it)) } }
        if (state.showBrowse) {
            item { SectionHeader(stringResource(R.string.feature_search_moods)) }
            item { TileGrid(MOODS) { onIntent(SearchIntent.QueryChanged(it)) } }
            item { SectionHeader(stringResource(R.string.feature_search_genres)) }
            item { TileGrid(GENRES) { onIntent(SearchIntent.QueryChanged(it)) } }
        } else {
            results(state, onIntent)
        }
    }
}

private fun LazyListScope.results(
    state: SearchState,
    onIntent: (SearchIntent) -> Unit,
) {
    if (state.source == SearchSource.Jamendo || state.source == SearchSource.Preview) {
        item { Hint(stringResource(R.string.feature_search_online_soon)) }
        return
    }
    if (state.searched && state.artists.isEmpty() && state.tracks.isEmpty()) {
        item { Hint(stringResource(R.string.feature_search_nothing, state.query)) }
        return
    }
    if (state.artists.isNotEmpty()) {
        item { SectionHeader(stringResource(R.string.feature_search_artists)) }
        items(state.artists, key = { "artist:" + it.name }) { artist ->
            ArtistRow(artist) { onIntent(SearchIntent.PlayArtist(artist.name)) }
        }
    }
    if (state.tracks.isNotEmpty()) {
        item { SectionHeader(stringResource(R.string.feature_search_tracks)) }
        itemsIndexed(state.tracks, key = { _, t -> t.id.value }) { index, track ->
            TrackRow(track = track, onClick = { onIntent(SearchIntent.PlayTrack(index)) })
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(24.dp))
                .padding(start = Spacing.l, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    stringResource(R.string.feature_search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.feature_search_clear))
            }
        }
    }
}

@Composable
private fun SourceChips(
    selected: SearchSource,
    onSelect: (SearchSource) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        items(SearchSource.entries) { source ->
            FuzzChip(text = stringResource(source.label()), selected = source == selected, onClick = {
                onSelect(source)
            })
        }
    }
}

private fun SearchSource.label() =
    when (this) {
        SearchSource.Everywhere -> R.string.feature_search_source_all
        SearchSource.Collection -> R.string.feature_search_source_collection
        SearchSource.Jamendo -> R.string.feature_search_source_jamendo
        SearchSource.Preview -> R.string.feature_search_source_preview
    }

private data class Tile(
    val label: Int,
    val hueFrom: Float,
    val hueTo: Float,
)

private val MOODS =
    listOf(
        Tile(R.string.feature_search_mood_energetic, 40f, 10f),
        Tile(R.string.feature_search_mood_calm, 190f, 220f),
        Tile(R.string.feature_search_mood_sad, 230f, 270f),
        Tile(R.string.feature_search_mood_aggressive, 350f, 10f),
    )

private val GENRES =
    listOf(
        Tile(R.string.feature_search_genre_rock, 10f, 330f),
        Tile(R.string.feature_search_genre_electronic, 200f, 260f),
        Tile(R.string.feature_search_genre_hiphop, 40f, 20f),
        Tile(R.string.feature_search_genre_jazz, 30f, 350f),
        Tile(R.string.feature_search_genre_metal, 270f, 300f),
        Tile(R.string.feature_search_genre_indie, 140f, 190f),
    )

@Composable
private fun TileGrid(
    tiles: List<Tile>,
    onTile: (String) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { tile ->
                    val text = stringResource(tile.label)
                    GradientTile(
                        text = text,
                        hueFrom = tile.hueFrom,
                        hueTo = tile.hueTo,
                        onClick = { onTile(text) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ArtistRow(
    artist: ArtistHit,
    onPlay: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth().clickable(
                onClick = onPlay,
            ).padding(horizontal = Spacing.screen, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(
            Modifier.size(48.dp).background(FuzzTheme.colors.accentSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                artist.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                artist.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.feature_search_artist_tracks, artist.trackCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Rounded.GraphicEq,
            contentDescription = stringResource(R.string.feature_search_play_artist),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
