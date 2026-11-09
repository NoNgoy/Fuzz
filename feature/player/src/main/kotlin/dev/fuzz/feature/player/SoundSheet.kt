package dev.fuzz.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fuzz.core.design.theme.MonoLabel
import dev.fuzz.core.design.theme.Spacing
import dev.fuzz.core.domain.player.EqualizerPreset
import dev.fuzz.core.domain.player.SoundSettings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SoundSheet(
    onDismiss: () -> Unit,
    viewModel: SoundViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        SoundContent(state, viewModel::onIntent, Modifier.verticalScroll(rememberScrollState()))
    }
}

@Composable
internal fun SoundScreen(
    onBack: () -> Unit,
    viewModel: SoundViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = Spacing.xs)) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.feature_player_back),
            )
        }
        SoundContent(state, viewModel::onIntent)
    }
}

@Composable
internal fun SoundContent(
    state: SoundState,
    onIntent: (SoundIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(stringResource(R.string.feature_player_sound_title), style = MaterialTheme.typography.headlineSmall)
        SwitchRow(stringResource(R.string.feature_player_sound_equalizer), settings.equalizerEnabled) {
            onIntent(SoundIntent.SetEqualizer(it))
        }
        if (state.bands == null) {
            Text(
                stringResource(R.string.feature_player_sound_no_equalizer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Equalizer(state, onIntent)
        }
        HorizontalDivider(Modifier.padding(vertical = Spacing.s))
        Fade(settings.fadeSeconds) { onIntent(SoundIntent.SetFade(it)) }
        SwitchRow(
            stringResource(R.string.feature_player_sound_skip_silence),
            settings.skipSilence,
            hint = stringResource(R.string.feature_player_sound_skip_silence_hint),
        ) { onIntent(SoundIntent.SetSkipSilence(it)) }
        SwitchRow(
            stringResource(R.string.feature_player_sound_normalize),
            settings.normalize,
            hint = stringResource(R.string.feature_player_sound_normalize_hint),
        ) { onIntent(SoundIntent.SetNormalize(it)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Equalizer(
    state: SoundState,
    onIntent: (SoundIntent) -> Unit,
) {
    val bands = state.bands ?: return
    val settings = state.settings
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        EqualizerPreset.entries.forEach { preset ->
            FilterChip(
                selected = settings.equalizerEnabled && settings.preset == preset,
                onClick = { onIntent(SoundIntent.PickPreset(preset)) },
                label = { Text(stringResource(preset.label)) },
            )
        }
        FilterChip(
            selected = settings.equalizerEnabled && settings.preset == null,
            onClick = { onIntent(SoundIntent.SetBand(0, state.gains.firstOrNull() ?: 0f)) },
            label = { Text(stringResource(R.string.feature_player_sound_preset_custom)) },
        )
    }
    state.gains.forEachIndexed { index, db ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                frequency(bands.centersHz[index]),
                style = MonoLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(56.dp),
            )
            Slider(
                value = db,
                onValueChange = { onIntent(SoundIntent.SetBand(index, it)) },
                valueRange = bands.minDb..bands.maxDb,
                enabled = settings.equalizerEnabled,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.feature_player_sound_db, db.roundToInt()),
                style = MonoLabel,
                modifier = Modifier.width(52.dp).padding(start = Spacing.s),
            )
        }
    }
    if (bands.hasBassBoost) {
        Text(stringResource(R.string.feature_player_sound_bass), style = MaterialTheme.typography.titleSmall)
        Slider(
            value = settings.bassBoost,
            onValueChange = { onIntent(SoundIntent.SetBassBoost(it)) },
            enabled = settings.equalizerEnabled,
        )
    }
}

@Composable
private fun Fade(
    seconds: Int,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.feature_player_sound_fade),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (seconds == 0) {
                stringResource(R.string.feature_player_sound_fade_off)
            } else {
                stringResource(R.string.feature_player_sound_fade_seconds, seconds)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Slider(
        value = seconds.toFloat(),
        onValueChange = { onChange(it.roundToInt()) },
        valueRange = 0f..SoundSettings.MAX_FADE_SECONDS.toFloat(),
        steps = SoundSettings.MAX_FADE_SECONDS - 1,
    )
    Text(
        stringResource(R.string.feature_player_sound_fade_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    hint: String? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            hint?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun frequency(hz: Int): String =
    if (hz < KILO) {
        stringResource(R.string.feature_player_sound_hz, hz)
    } else {
        val khz = hz / KILO.toFloat()
        val locale = LocalConfiguration.current.locales[0]
        val text = if (khz < TEN) String.format(locale, "%.1f", khz) else khz.roundToInt().toString()
        stringResource(R.string.feature_player_sound_khz, text)
    }

private const val KILO = 1_000
private const val TEN = 10f

private val EqualizerPreset.label: Int
    get() =
        when (this) {
            EqualizerPreset.Flat -> R.string.feature_player_sound_preset_flat
            EqualizerPreset.Bass -> R.string.feature_player_sound_preset_bass
            EqualizerPreset.Vocal -> R.string.feature_player_sound_preset_vocal
            EqualizerPreset.Rock -> R.string.feature_player_sound_preset_rock
            EqualizerPreset.Electronic -> R.string.feature_player_sound_preset_electronic
            EqualizerPreset.Acoustic -> R.string.feature_player_sound_preset_acoustic
            EqualizerPreset.Night -> R.string.feature_player_sound_preset_night
        }
