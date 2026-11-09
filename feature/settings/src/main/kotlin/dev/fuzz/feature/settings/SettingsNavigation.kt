package dev.fuzz.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

/** Куда ведут пункты настроек; все экраны живут в своих модулях, здесь только переходы. */
data class SettingsActions(
    val onBack: () -> Unit = {},
    val onOpenFolders: () -> Unit = {},
    val onOpenSound: () -> Unit = {},
    val onOpenWave: () -> Unit = {},
    val onOpenConnections: () -> Unit = {},
    val onOpenImport: () -> Unit = {},
    val onSaveBackup: () -> Unit = {},
    val onRestoreBackup: () -> Unit = {},
)

/** Вкладка «Профиль»: статистика, итоги года, импорт, настройки. */
@Serializable
data object ProfileRoute

fun NavGraphBuilder.settingsScreen(actions: SettingsActions) {
    composable<SettingsRoute> { SettingsEntry(actions) }
}

fun NavGraphBuilder.profileScreen(
    onOpenImport: () -> Unit,
    onOpenConnections: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSound: () -> Unit,
    onOpenWave: () -> Unit,
) {
    composable<ProfileRoute> {
        ProfileEntry(
            onOpenImport = onOpenImport,
            onOpenConnections = onOpenConnections,
            onOpenSettings = onOpenSettings,
            onOpenSound = onOpenSound,
            onOpenWave = onOpenWave,
        )
    }
}
