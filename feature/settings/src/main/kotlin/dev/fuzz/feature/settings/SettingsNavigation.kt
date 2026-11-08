package dev.fuzz.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

/** Вкладка «Профиль»: статистика, итоги года, импорт, настройки. */
@Serializable
data object ProfileRoute

fun NavGraphBuilder.settingsScreen() {
    composable<SettingsRoute> { SettingsScreen() }
}

fun NavGraphBuilder.profileScreen(
    onOpenImport: () -> Unit,
    onOpenConnections: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    composable<ProfileRoute> {
        ProfileScreen(
            onOpenImport = onOpenImport,
            onOpenConnections = onOpenConnections,
            onOpenSettings = onOpenSettings,
        )
    }
}
