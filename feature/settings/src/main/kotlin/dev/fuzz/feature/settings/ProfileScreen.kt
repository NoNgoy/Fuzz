package dev.fuzz.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.fuzz.core.design.theme.FuzzTheme
import dev.fuzz.core.design.theme.Spacing
import dev.fuzz.core.design.theme.Unbounded

/**
 * Профиль: статистика, итоги года и служебное. Цифры статистики появятся вместе с историей
 * прослушиваний (этап 2), пока показываются нули.
 */
@Composable
internal fun ProfileScreen(
    onOpenImport: () -> Unit,
    onOpenConnections: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSound: () -> Unit = {},
    onOpenWave: () -> Unit = {},
) {
    Column(
        modifier =
            modifier
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.section),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).background(FuzzTheme.colors.accentSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "F",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = Unbounded),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(Modifier.weight(1f).padding(start = Spacing.m)) {
                Text(
                    stringResource(R.string.feature_settings_profile_name),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(R.string.feature_settings_profile_since),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.feature_settings_title))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Stat("0", stringResource(R.string.feature_settings_stat_hours), Modifier.weight(1f))
            Stat("0", stringResource(R.string.feature_settings_stat_new_tracks), Modifier.weight(1f))
            Stat("0", stringResource(R.string.feature_settings_stat_new_artists), Modifier.weight(1f))
        }
        YearBanner()
        Column {
            MenuRow(
                Icons.Rounded.FileUpload,
                R.string.feature_settings_menu_import,
                R.string.feature_settings_menu_import_hint,
                onOpenImport,
            )
            MenuRow(
                Icons.Outlined.Power,
                R.string.feature_settings_menu_connections,
                R.string.feature_settings_menu_connections_hint,
                onOpenConnections,
            )
            MenuRow(
                Icons.Outlined.Equalizer,
                R.string.feature_settings_menu_sound,
                R.string.feature_settings_menu_sound_hint,
                onOpenSound,
            )
            MenuRow(
                Icons.Rounded.GraphicEq,
                R.string.feature_settings_menu_wave,
                R.string.feature_settings_menu_wave_hint,
                onOpenWave,
            )
        }
    }
}

@Composable
private fun Stat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = Spacing.s),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun YearBanner() {
    val hue = (FuzzTheme.colors.accentHue + BANNER_HUE_SHIFT) % FULL_CIRCLE
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(FuzzTheme.colors.lamp, Color.hsl(hue, 0.9f, 0.62f))),
                    RoundedCornerShape(20.dp),
                ).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.feature_settings_year_title),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = Unbounded),
                color = FuzzTheme.colors.onLamp,
            )
            Text(
                stringResource(R.string.feature_settings_year_body),
                style = MaterialTheme.typography.bodySmall,
                color = FuzzTheme.colors.onLamp,
            )
        }
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = FuzzTheme.colors.onLamp)
    }
}

@Composable
internal fun MenuRow(
    icon: ImageVector,
    title: Int,
    subtitle: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(
            Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val BANNER_HUE_SHIFT = 150f
private const val FULL_CIRCLE = 360f
