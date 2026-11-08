package dev.fuzz.feature.wave

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fuzz.core.design.component.WaveBars
import dev.fuzz.core.design.theme.FuzzTheme
import dev.fuzz.core.design.theme.Spacing
import dev.fuzz.core.domain.wave.WaveShare
import dev.fuzz.core.domain.wave.WaveSource
import dev.fuzz.core.model.wave.WaveActivity
import dev.fuzz.core.model.wave.WaveCharacter
import dev.fuzz.core.model.wave.WaveMood
import dev.fuzz.core.model.wave.WaveSettings
import dev.fuzz.core.ui.mvi.CollectEffects
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.roundToInt

@Composable
internal fun WaveEntry(
    onBack: () -> Unit,
    viewModel: WaveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            WaveEffect.LibraryEmpty -> {
                val text = resources.getString(R.string.feature_wave_library_empty)
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            }
        }
    }
    WaveScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}

@Composable
internal fun WaveScreen(
    state: WaveState,
    onIntent: (WaveIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.section),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.feature_wave_back))
            }
            Text(
                stringResource(R.string.feature_wave_title),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        WaveHeader(state, onPlayPause = { onIntent(WaveIntent.PlayPause) })
        FamiliaritySlider(state.familiarity, state.mix, onIntent)
        ChipSection(
            title = R.string.feature_wave_activity,
            values = WaveActivity.entries,
            selected = state.settings.activity,
            label = { it.label },
            onToggle = { onIntent(WaveIntent.ToggleActivity(it)) },
        )
        ChipSection(
            title = R.string.feature_wave_mood,
            values = WaveMood.entries,
            selected = state.settings.mood,
            label = { it.label },
            onToggle = { onIntent(WaveIntent.ToggleMood(it)) },
        )
        ChipSection(
            title = R.string.feature_wave_character,
            values = WaveCharacter.entries,
            selected = state.settings.character,
            label = { it.label },
            onToggle = { onIntent(WaveIntent.ToggleCharacter(it)) },
        )
        TextButton(onClick = { onIntent(WaveIntent.Reset) }, enabled = state.settings != WaveSettings()) {
            Text(stringResource(R.string.feature_wave_reset))
        }
    }
}

@Composable
private fun WaveHeader(
    state: WaveState,
    onPlayPause: () -> Unit,
) {
    val hue = FuzzTheme.colors.accentHue
    val gradient =
        Brush.linearGradient(
            listOf(Color.hsl(hue, 0.85f, 0.44f), Color.hsl((hue + HUE_SHIFT) % FULL_CIRCLE, 0.80f, 0.32f)),
        )
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(gradient)
                .padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Tag(stringResource(R.string.feature_wave_tag))
            Tag(stringResource(mixLabel(state.familiarity), state.familiarity), ghost = true)
        }
        WaveBars(
            playing = state.playing,
            barColor = Color.White.copy(alpha = BAR_ALPHA),
            peakColor = FuzzTheme.colors.lamp,
            modifier = Modifier.fillMaxWidth().height(78.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.feature_wave_card_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                Text(
                    summary(state.settings),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = SUBTITLE_ALPHA),
                )
            }
            FilledIconButton(
                onClick = onPlayPause,
                enabled = !state.starting,
                modifier = Modifier.size(52.dp),
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                Icon(
                    imageVector = if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription =
                        stringResource(if (state.playing) R.string.feature_wave_pause else R.string.feature_wave_play),
                )
            }
        }
    }
}

@Composable
private fun summary(settings: WaveSettings): String {
    val parts =
        listOfNotNull(
            settings.activity?.let { stringResource(it.label) },
            settings.mood?.let { stringResource(it.label) },
            settings.character?.let { stringResource(it.label) },
        )
    return if (parts.isEmpty()) stringResource(R.string.feature_wave_no_filters) else parts.joinToString(" · ")
}

@Composable
private fun FamiliaritySlider(
    familiarity: Int,
    mix: ImmutableList<WaveShare>,
    onIntent: (WaveIntent) -> Unit,
) {
    val sliderLabel = stringResource(R.string.feature_wave_slider)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.feature_wave_familiar), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.feature_wave_new), style = MaterialTheme.typography.titleSmall)
        }
        Slider(
            value = familiarity.toFloat(),
            onValueChange = { onIntent(WaveIntent.DragFamiliarity(it.roundToInt())) },
            onValueChangeFinished = { onIntent(WaveIntent.CommitFamiliarity) },
            valueRange = 0f..WaveSettings.MAX_FAMILIARITY.toFloat(),
            colors =
                SliderDefaults.colors(
                    thumbColor = FuzzTheme.colors.lamp,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = FuzzTheme.colors.accentSoft,
                ),
            modifier = Modifier.semantics { contentDescription = sliderLabel },
        )
        MixBar(mix)
        Legend(mix)
    }
}

@Composable
private fun MixBar(mix: ImmutableList<WaveShare>) {
    Row(
        Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        mix.forEach { share ->
            Box(Modifier.weight(share.share.coerceAtLeast(MIN_WEIGHT)).fillMaxSize().background(share.source.color()))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(mix: ImmutableList<WaveShare>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = 2,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        mix.forEach { share ->
            Row(
                Modifier.weight(1f).padding(end = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(share.source.color()))
                Text(
                    stringResource(
                        R.string.feature_wave_share,
                        stringResource(share.source.label),
                        (share.share * PERCENT).roundToInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WaveSource.color(): Color {
    val accent = MaterialTheme.colorScheme.primary
    return when (this) {
        WaveSource.Favorites -> accent
        WaveSource.Unheard -> accent.copy(alpha = UNHEARD_ALPHA)
        WaveSource.Similar -> accent.copy(alpha = SIMILAR_ALPHA)
        WaveSource.OpenCatalog -> FuzzTheme.colors.lamp
        WaveSource.Preview -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipSection(
    title: Int,
    values: List<T>,
    selected: T?,
    label: (T) -> Int,
    onToggle: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            values.forEach { value ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onToggle(value) },
                    label = { Text(stringResource(label(value))) },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FuzzTheme.colors.accentSoft,
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                )
            }
        }
    }
}

@Composable
private fun Tag(
    text: String,
    ghost: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = if (ghost) Color.Transparent else Color.White.copy(alpha = TAG_ALPHA),
        contentColor = Color.White,
        border = if (ghost) BorderStroke(1.dp, Color.White.copy(alpha = GHOST_ALPHA)) else null,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private const val HUE_SHIFT = 70f
private const val FULL_CIRCLE = 360f
private const val BAR_ALPHA = 0.85f
private const val SUBTITLE_ALPHA = 0.85f
private const val TAG_ALPHA = 0.18f
private const val GHOST_ALPHA = 0.4f
private const val UNHEARD_ALPHA = 0.65f
private const val SIMILAR_ALPHA = 0.38f
private const val MIN_WEIGHT = 0.01f
private const val PERCENT = 100
