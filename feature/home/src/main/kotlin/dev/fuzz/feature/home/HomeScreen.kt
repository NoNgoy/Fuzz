package dev.fuzz.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fuzz.core.design.component.ScreenTitle
import dev.fuzz.core.design.component.SectionHeader
import dev.fuzz.core.design.theme.FuzzTheme
import dev.fuzz.core.design.theme.Spacing
import dev.fuzz.core.design.theme.Unbounded
import dev.fuzz.core.ui.component.TrackRow
import dev.fuzz.core.ui.mvi.CollectEffects
import java.time.LocalDateTime

@Composable
internal fun HomeEntry(
    onOpenWaveSettings: () -> Unit,
    onAddMusic: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val emptyMessage = stringResource(R.string.feature_home_library_empty_toast)
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            HomeEffect.LibraryEmpty -> snackbar.showSnackbar(emptyMessage)
        }
    }
    Box {
        HomeScreen(
            state = state,
            onIntent = viewModel::onIntent,
            onOpenWaveSettings = onOpenWaveSettings,
            onAddMusic = onAddMusic,
        )
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
internal fun HomeScreen(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onOpenWaveSettings: () -> Unit,
    onAddMusic: () -> Unit,
    modifier: Modifier = Modifier,
    now: LocalDateTime = LocalDateTime.now(),
) {
    LazyColumn(
        modifier = modifier.statusBarsPadding(),
        contentPadding = PaddingValues(vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        item { Greeting(now) }
        item {
            WaveCard(
                isPlaying = state.isPlaying,
                familiarity = state.familiarity,
                onPlay = { onIntent(HomeIntent.PlayWave) },
                onOpenSettings = onOpenWaveSettings,
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s),
            )
        }
        if (!state.isLoading && state.libraryEmpty) {
            item { AddMusicPanel(onAddMusic, Modifier.padding(Spacing.screen)) }
        }
        if (state.recentlyAdded.isNotEmpty()) {
            item {
                Spacer(Modifier.height(Spacing.s))
                SectionHeader(stringResource(R.string.feature_home_recently_added))
            }
            itemsIndexed(state.recentlyAdded, key = { _, t -> t.id.value }) { index, track ->
                TrackRow(track = track, onClick = { onIntent(HomeIntent.PlayTrack(index)) })
            }
        }
    }
}

@Composable
private fun Greeting(now: LocalDateTime) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = dayPartLabel(now),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ScreenTitle(stringResource(R.string.feature_home_greeting))
        }
        Box(
            modifier =
                Modifier
                    .size(38.dp)
                    .background(FuzzTheme.colors.accentSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.feature_home_avatar_letter),
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = Unbounded),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun dayPartLabel(now: LocalDateTime): String {
    val days = stringArrayResource(R.array.feature_home_weekdays)
    val part =
        when (now.hour) {
            in MORNING -> R.string.feature_home_morning
            in DAY -> R.string.feature_home_day
            in EVENING -> R.string.feature_home_evening
            else -> R.string.feature_home_night
        }
    return stringResource(R.string.feature_home_day_part, days[now.dayOfWeek.value - 1], stringResource(part))
}

@Composable
private fun AddMusicPanel(
    onAddMusic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
                .padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(stringResource(R.string.feature_home_empty_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.feature_home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAddMusic) { Text(stringResource(R.string.feature_home_empty_action)) }
    }
}

private val MORNING = 5..11
private val DAY = 12..16
private val EVENING = 17..22
