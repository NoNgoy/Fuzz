package dev.fuzz.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.fuzz.app.navigation.FuzzApp
import dev.fuzz.core.design.theme.ColorSource
import dev.fuzz.core.design.theme.FuzzTheme
import dev.fuzz.core.design.theme.FuzzThemeConfig
import dev.fuzz.core.design.theme.ThemeMode
import dev.fuzz.core.domain.settings.AccentPreference
import dev.fuzz.core.domain.settings.AppearanceSettings
import dev.fuzz.core.domain.settings.ThemePreference

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Сплэш держится, пока не прочитали, пройден ли онбординг: так стартовый экран не мигает.
        installSplashScreen().setKeepOnScreenCondition { viewModel.launchState.value == LaunchState.Loading }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val launch by viewModel.launchState.collectAsStateWithLifecycle()
            FuzzTheme(config = (launch as? LaunchState.Ready)?.appearance.toThemeConfig()) {
                val ready = launch as? LaunchState.Ready ?: return@FuzzTheme
                // Стартовый экран выбирается один раз: дальнейшие изменения флага не пересобирают граф.
                val showOnboarding = remember { !ready.onboardingCompleted }
                FuzzApp(
                    showOnboarding = showOnboarding,
                    playerErrors = viewModel.playerErrors,
                    downloadEvents = viewModel.downloadEvents,
                )
            }
        }
    }
}

private fun AppearanceSettings?.toThemeConfig(): FuzzThemeConfig {
    if (this == null) return FuzzThemeConfig()
    return FuzzThemeConfig(
        mode =
            when (theme) {
                ThemePreference.System -> ThemeMode.System
                ThemePreference.Light -> ThemeMode.Light
                ThemePreference.Dark -> ThemeMode.Dark
                ThemePreference.Black -> ThemeMode.Amoled
            },
        colorSource =
            when (accent) {
                AccentPreference.Brand -> ColorSource.Brand
                AccentPreference.Wallpaper -> ColorSource.Wallpaper
            },
    )
}
