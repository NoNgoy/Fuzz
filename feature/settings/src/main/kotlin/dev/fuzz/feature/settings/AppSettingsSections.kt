package dev.fuzz.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import dev.fuzz.core.design.component.FuzzChip
import dev.fuzz.core.design.theme.Spacing
import dev.fuzz.core.domain.settings.AppSettings
import dev.fuzz.core.domain.settings.ScanRules
import dev.fuzz.core.domain.settings.StreamQuality

/** Исключения сканера: записи и короткие файлы. */
@Composable
internal fun ScanSettings(
    rules: ScanRules,
    onChange: (ScanRules) -> Unit,
) {
    SettingSwitch(
        R.string.feature_settings_skip_voice,
        R.string.feature_settings_skip_voice_hint,
        rules.skipVoiceNotes,
    ) { onChange(rules.copy(skipVoiceNotes = it)) }
    Label(R.string.feature_settings_min_duration)
    Choices(ScanRules.DURATION_CHOICES, rules.minDurationSec, { durationLabel(it) }) {
        onChange(rules.copy(minDurationSec = it))
    }
}

/** Наушники, жесты, качество и кэш. */
@Composable
internal fun PlaybackSettings(
    app: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    SettingSwitch(
        R.string.feature_settings_pause_on_disconnect,
        R.string.feature_settings_pause_on_disconnect_hint,
        app.pauseOnDisconnect,
    ) { value -> onChange { it.copy(pauseOnDisconnect = value) } }
    SettingSwitch(
        R.string.feature_settings_gestures,
        R.string.feature_settings_gestures_hint,
        app.artworkGestures,
    ) { value -> onChange { it.copy(artworkGestures = value) } }
    Label(R.string.feature_settings_quality_wifi)
    Choices(StreamQuality.entries, app.wifiQuality, { qualityLabel(it) }) { value ->
        onChange { it.copy(wifiQuality = value) }
    }
    Label(R.string.feature_settings_quality_mobile)
    Choices(StreamQuality.entries, app.mobileQuality, { qualityLabel(it) }) { value ->
        onChange { it.copy(mobileQuality = value) }
    }
    Hint(R.string.feature_settings_quality_hint)
    Label(R.string.feature_settings_cache)
    Choices(AppSettings.CACHE_CHOICES_MB, app.cacheLimitMb, { cacheLabel(it) }) { value ->
        onChange { it.copy(cacheLimitMb = value) }
    }
    Hint(R.string.feature_settings_cache_hint)
}

@Composable
private fun SettingSwitch(
    title: Int,
    hint: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Spacing.m)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choices(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        options.forEach { option ->
            FuzzChip(text = label(option), selected = option == selected, onClick = { onSelect(option) })
        }
    }
}

@Composable
private fun Hint(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun durationLabel(seconds: Int): String =
    if (seconds == 0) {
        stringResource(R.string.feature_settings_min_duration_any)
    } else {
        stringResource(R.string.feature_settings_seconds, seconds)
    }

@Composable
private fun qualityLabel(quality: StreamQuality): String =
    quality.kbps?.let { stringResource(R.string.feature_settings_kbps, it) }
        ?: stringResource(R.string.feature_settings_quality_original)

@Composable
private fun cacheLabel(megabytes: Int): String =
    when {
        megabytes == 0 -> stringResource(R.string.feature_settings_cache_off)
        megabytes < MB_IN_GB -> stringResource(R.string.feature_settings_megabytes, megabytes)
        else -> stringResource(R.string.feature_settings_gigabytes, megabytes / MB_IN_GB)
    }

private const val MB_IN_GB = 1_024
