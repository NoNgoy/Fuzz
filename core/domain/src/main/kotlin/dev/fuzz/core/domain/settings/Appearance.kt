package dev.fuzz.core.domain.settings

import kotlinx.coroutines.flow.Flow

/** Тема приложения: как в системе, светлая, тёмная или чёрная для AMOLED-экранов. */
enum class ThemePreference { System, Light, Dark, Black }

/** Откуда брать акцентный цвет: фирменный цвет Fuzz или цвета обоев (Android 12+). */
enum class AccentPreference { Brand, Wallpaper }

data class AppearanceSettings(
    val theme: ThemePreference = ThemePreference.System,
    val accent: AccentPreference = AccentPreference.Brand,
)

interface AppearanceRepository {
    val settings: Flow<AppearanceSettings>

    suspend fun setTheme(theme: ThemePreference)

    suspend fun setAccent(accent: AccentPreference)
}
