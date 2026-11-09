package dev.fuzz.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fuzz.core.design.component.ScreenTitle
import dev.fuzz.core.design.theme.FuzzTheme
import dev.fuzz.core.design.theme.Spacing
import dev.fuzz.core.design.theme.Unbounded
import dev.fuzz.core.domain.stats.YearRecap

@Composable
internal fun YearRecapEntry(
    onBack: () -> Unit,
    viewModel: YearRecapViewModel = hiltViewModel(),
) {
    val recap by viewModel.recap.collectAsStateWithLifecycle()
    YearRecapScreen(recap, onBack)
}

/** Итоги года: часы, любимые артисты и треки, самый музыкальный месяц. До декабря — «пока что». */
@Composable
internal fun YearRecapScreen(
    recap: YearRecap?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isDecember: Boolean = java.time.LocalDate.now().monthValue == DECEMBER,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.section),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = Spacing.xs)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.feature_settings_back))
        }
        if (recap == null) return@Column
        Column(Modifier.padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ScreenTitle(stringResource(R.string.feature_settings_year_screen, recap.year))
            Text(
                stringResource(
                    when {
                        recap.isEmpty -> R.string.feature_settings_year_empty
                        isDecember -> R.string.feature_settings_year_final
                        else -> R.string.feature_settings_year_so_far
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (recap.isEmpty) return@Column
        Hero(recap)
        Facts(recap)
        if (recap.topArtists.isNotEmpty()) {
            Ranked(
                stringResource(R.string.feature_settings_year_artists),
                recap.topArtists.map {
                    it.name to stringResource(R.string.feature_settings_year_hours_short, formatHours(it.listenedMs))
                },
            )
        }
        if (recap.topTracks.isNotEmpty()) {
            Ranked(
                stringResource(R.string.feature_settings_year_tracks),
                recap.topTracks.map {
                    val plays = pluralStringResource(R.plurals.feature_settings_year_plays, it.plays, it.plays)
                    "${it.title} · ${it.artist}" to plays
                },
            )
        }
    }
}

@Composable
private fun Hero(recap: YearRecap) {
    Column(
        Modifier
            .padding(horizontal = Spacing.screen)
            .fillMaxWidth()
            .background(yearGradient(), RoundedCornerShape(24.dp))
            .padding(20.dp),
    ) {
        Text(
            formatHours(recap.listenedMs),
            style = MaterialTheme.typography.displayMedium.copy(fontFamily = Unbounded),
            color = FuzzTheme.colors.onLamp,
        )
        Text(
            stringResource(R.string.feature_settings_year_hours),
            style = MaterialTheme.typography.titleMedium,
            color = FuzzTheme.colors.onLamp,
        )
        recap.topArtists.firstOrNull()?.let {
            Text(
                stringResource(R.string.feature_settings_year_favourite, it.name),
                style = MaterialTheme.typography.bodyMedium,
                color = FuzzTheme.colors.onLamp,
                modifier = Modifier.padding(top = Spacing.s),
            )
        }
    }
}

@Composable
private fun Facts(recap: YearRecap) {
    val months = stringArrayResource(R.array.feature_settings_months)
    Column(Modifier.padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Fact(
                recap.plays.toString(),
                stringResource(R.string.feature_settings_year_plays_label),
                Modifier.weight(1f),
            )
            Fact(
                recap.distinctTracks.toString(),
                stringResource(R.string.feature_settings_year_distinct),
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Fact(
                recap.topMonth?.let { months[it - 1] } ?: "—",
                stringResource(R.string.feature_settings_year_month),
                Modifier.weight(1f),
            )
            Fact(
                recap.newArtists.toString(),
                stringResource(R.string.feature_settings_year_new_artists),
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Fact(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .padding(14.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Ranked(
    title: String,
    rows: List<Pair<String, String>>,
) {
    Column(Modifier.padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        rows.forEachIndexed { index, (name, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = Unbounded),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(32.dp),
                )
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.s),
                )
            }
        }
    }
}

private const val DECEMBER = 12
