package dev.fuzz.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.fuzz.core.design.theme.Spacing

@Composable
internal fun SettingsScreen(
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    version: String = appVersion(),
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Box(Modifier.fillMaxWidth()) {
            IconButton(onClick = actions.onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.feature_settings_back))
            }
            Text(
                stringResource(R.string.feature_settings_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        MenuRow(
            Icons.Outlined.Folder,
            R.string.feature_settings_folders,
            R.string.feature_settings_folders_hint,
            actions.onOpenFolders,
        )
        MenuRow(
            Icons.Outlined.Equalizer,
            R.string.feature_settings_menu_sound,
            R.string.feature_settings_menu_sound_hint,
            actions.onOpenSound,
        )
        MenuRow(
            Icons.Rounded.GraphicEq,
            R.string.feature_settings_menu_wave,
            R.string.feature_settings_menu_wave_hint,
            actions.onOpenWave,
        )
        MenuRow(
            Icons.Outlined.Power,
            R.string.feature_settings_menu_connections,
            R.string.feature_settings_menu_connections_hint,
            actions.onOpenConnections,
        )
        MenuRow(
            Icons.Rounded.FileUpload,
            R.string.feature_settings_menu_import,
            R.string.feature_settings_menu_import_hint,
            actions.onOpenImport,
        )
        Text(
            stringResource(R.string.feature_settings_version, version),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.l),
        )
    }
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
}
