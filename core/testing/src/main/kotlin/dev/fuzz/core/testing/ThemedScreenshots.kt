package dev.fuzz.core.testing

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size
import dev.fuzz.core.design.theme.FuzzTheme
import dev.fuzz.core.design.theme.FuzzThemeConfig
import dev.fuzz.core.design.theme.ThemeMode

/** Темы, в которых снимается каждый экран: светлая, тёмная и AMOLED (раздел 6 ТЗ). */
val ScreenshotThemes = listOf(ThemeMode.Light, ThemeMode.Dark, ThemeMode.Amoled)

/** Размер экрана Poco F5 в dp: 1080×2400 при 440 dpi. */
const val PHONE_WIDTH_DP = 393
const val PHONE_HEIGHT_DP = 873

/**
 * Снимает [content] во всех [ScreenshotThemes] в `src/test/screenshots/<name>_<тема>.png`.
 * Запись эталонов: `./gradlew recordRoborazziDebug`, сравнение: `verifyRoborazziDebug`.
 */
@OptIn(ExperimentalRoborazziApi::class)
fun captureThemedScreens(
    name: String,
    content: @Composable () -> Unit,
) {
    ScreenshotThemes.forEach { mode ->
        captureRoboImage(
            filePath = "src/test/screenshots/${name}_${mode.name.lowercase()}.png",
            // Половина разрешения: разницу в вёрстке видно, а репозиторий не раздувается.
            roborazziOptions = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5)),
            roborazziComposeOptions =
                RoborazziComposeOptions {
                    size(widthDp = PHONE_WIDTH_DP, heightDp = PHONE_HEIGHT_DP)
                },
        ) {
            FuzzTheme(FuzzThemeConfig(mode = mode)) {
                // Как Scaffold в приложении: фон и цвет текста по умолчанию берутся из темы.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
    }
}
