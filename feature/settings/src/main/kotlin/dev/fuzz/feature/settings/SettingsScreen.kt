package dev.fuzz.feature.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fuzz.core.design.component.FuzzChip
import dev.fuzz.core.design.theme.Spacing
import dev.fuzz.core.domain.backup.RestoreSummary
import dev.fuzz.core.domain.library.SyncOutcome
import dev.fuzz.core.domain.settings.AccentPreference
import dev.fuzz.core.domain.settings.ThemePreference
import dev.fuzz.core.ui.mvi.CollectEffects

@Composable
internal fun SettingsEntry(
    actions: SettingsActions,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    CollectEffects(viewModel.effects) { effect ->
        val text =
            when (effect) {
                is SettingsEffect.Rescanned -> {
                    when (val outcome = effect.outcome) {
                        SyncOutcome.NoPermission -> {
                            resources.getString(R.string.feature_settings_rescan_no_permission)
                        }

                        SyncOutcome.NoFolders -> {
                            resources.getString(R.string.feature_settings_rescan_no_folders)
                        }

                        is SyncOutcome.Synced -> {
                            resources.getString(
                                R.string.feature_settings_rescan_done,
                                outcome.stats.added,
                                outcome.stats.removed,
                            )
                        }
                    }
                }

                is SettingsEffect.Restored -> {
                    resources.getQuantityString(R.plurals.feature_settings_restored, effect.count, effect.count)
                }

                SettingsEffect.HistoryCleared -> {
                    resources.getString(R.string.feature_settings_history_cleared)
                }

                is SettingsEffect.BackupSaved -> {
                    resources.getString(
                        R.string.feature_settings_backup_saved,
                        effect.summary.likes,
                        effect.summary.playlists,
                        effect.summary.listens,
                    )
                }

                is SettingsEffect.BackupRestored -> {
                    restoredText(resources, effect.summary)
                }

                is SettingsEffect.BackupFailed -> {
                    resources.getString(
                        if (effect.notBackup) {
                            R.string.feature_settings_backup_not_fuzz
                        } else {
                            R.string.feature_settings_backup_io_failed
                        },
                    )
                }
            }
        snackbar.showSnackbar(text)
    }
    val save =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME)) { uri ->
            uri?.let { viewModel.onIntent(SettingsIntent.ExportBackup(it.toString())) }
        }
    val open =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { viewModel.onIntent(SettingsIntent.RestoreBackup(it.toString())) }
        }
    Box(Modifier.fillMaxSize()) {
        SettingsScreen(
            state = state,
            onIntent = viewModel::onIntent,
            actions =
                actions.copy(
                    onSaveBackup = { save.launch(backupFileName()) },
                    onRestoreBackup = { open.launch(arrayOf(BACKUP_MIME, "application/octet-stream", "text/plain")) },
                ),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
internal fun SettingsScreen(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    version: String = appVersion(),
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
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

        Section(R.string.feature_settings_section_look)
        AppearancePicker(state, onIntent)

        Section(R.string.feature_settings_section_library)
        MenuRow(
            Icons.Outlined.Folder,
            R.string.feature_settings_folders,
            R.string.feature_settings_folders_hint,
            actions.onOpenFolders,
        )
        MenuRow(
            icon = Icons.Rounded.Refresh,
            title = stringResource(R.string.feature_settings_rescan),
            subtitle = stringResource(R.string.feature_settings_rescan_hint),
            onClick = { onIntent(SettingsIntent.Rescan) },
            enabled = !state.isRescanning,
            trailing =
                if (state.isRescanning) {
                    { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                } else {
                    null
                },
        )
        ScanSettings(state.app.scan) { onIntent(SettingsIntent.SetScanRules(it)) }
        if (state.hiddenTracks > 0) {
            MenuRow(
                icon = Icons.Outlined.Visibility,
                title = stringResource(R.string.feature_settings_restore_hidden),
                subtitle =
                    pluralStringResource(
                        R.plurals.feature_settings_hidden_count,
                        state.hiddenTracks,
                        state.hiddenTracks,
                    ),
                onClick = { onIntent(SettingsIntent.RestoreHidden) },
            )
        }

        Section(R.string.feature_settings_section_playback)
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
        PlaybackSettings(state.app) { onIntent(SettingsIntent.UpdateApp(it)) }

        Section(R.string.feature_settings_section_sources)
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

        Section(R.string.feature_settings_section_backup)
        MenuRow(
            icon = Icons.Outlined.SaveAlt,
            title = stringResource(R.string.feature_settings_backup_save),
            subtitle = stringResource(R.string.feature_settings_backup_save_hint),
            onClick = actions.onSaveBackup,
            enabled = !state.isBackingUp,
            trailing =
                if (state.isBackingUp) {
                    { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                } else {
                    null
                },
        )
        MenuRow(
            icon = Icons.Outlined.SettingsBackupRestore,
            title = stringResource(R.string.feature_settings_backup_restore),
            subtitle = stringResource(R.string.feature_settings_backup_restore_hint),
            onClick = actions.onRestoreBackup,
            enabled = !state.isBackingUp,
        )

        Section(R.string.feature_settings_section_privacy)
        MenuRow(
            Icons.Outlined.History,
            R.string.feature_settings_clear_history,
            R.string.feature_settings_clear_history_hint,
        ) { confirmClear = true }

        Text(
            stringResource(R.string.feature_settings_version, version),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.l),
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            text = { Text(stringResource(R.string.feature_settings_clear_history_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onIntent(SettingsIntent.ClearHistory)
                }) { Text(stringResource(R.string.feature_settings_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(dev.fuzz.core.ui.R.string.core_ui_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearancePicker(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
) {
    Label(R.string.feature_settings_theme)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        ThemePreference.entries.forEach { theme ->
            FuzzChip(
                text = stringResource(theme.label()),
                selected = state.appearance.theme == theme,
                onClick = { onIntent(SettingsIntent.SetTheme(theme)) },
            )
        }
    }
    // Цвета из обоев есть только с Android 12.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Label(R.string.feature_settings_accent)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            AccentPreference.entries.forEach { accent ->
                FuzzChip(
                    text = stringResource(accent.label()),
                    selected = state.appearance.accent == accent,
                    onClick = { onIntent(SettingsIntent.SetAccent(accent)) },
                )
            }
        }
    }
}

private fun ThemePreference.label(): Int =
    when (this) {
        ThemePreference.System -> R.string.feature_settings_theme_system
        ThemePreference.Light -> R.string.feature_settings_theme_light
        ThemePreference.Dark -> R.string.feature_settings_theme_dark
        ThemePreference.Black -> R.string.feature_settings_theme_black
    }

private fun AccentPreference.label(): Int =
    when (this) {
        AccentPreference.Brand -> R.string.feature_settings_accent_brand
        AccentPreference.Wallpaper -> R.string.feature_settings_accent_wallpaper
    }

@Composable
private fun Section(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.l, bottom = Spacing.xs),
    )
}

@Composable
internal fun Label(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.xs),
    )
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
}

private const val BACKUP_MIME = "application/json"

private fun backupFileName(): String = "fuzz-" + java.time.LocalDate.now() + ".json"

private fun restoredText(
    resources: android.content.res.Resources,
    summary: RestoreSummary,
): String =
    buildList {
        add(
            resources.getString(
                R.string.feature_settings_backup_restored,
                summary.likes,
                summary.playlists,
                summary.listens,
            ),
        )
        if (summary.missingFiles > 0) {
            add(
                resources.getQuantityString(
                    R.plurals.feature_settings_backup_missing,
                    summary.missingFiles,
                    summary.missingFiles,
                ),
            )
        }
        if (summary.connectionsNeedingPassword > 0) {
            add(resources.getString(R.string.feature_settings_backup_passwords, summary.connectionsNeedingPassword))
        }
    }.joinToString(" ")
