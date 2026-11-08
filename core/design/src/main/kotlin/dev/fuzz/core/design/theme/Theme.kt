package dev.fuzz.core.design.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Режим темы: как в системе, светлая, тёмная, чёрная AMOLED. */
enum class ThemeMode { System, Light, Dark, Amoled }

/** Источник акцента (раздел 6 ТЗ): цвет бренда, обои (Material You) или текущая обложка. */
enum class ColorSource { Brand, Wallpaper, Cover }

@Immutable
data class FuzzThemeConfig(
    val mode: ThemeMode = ThemeMode.System,
    val colorSource: ColorSource = ColorSource.Brand,
    /** Опорный цвет текущей обложки, если [colorSource] = [ColorSource.Cover]. Берётся только его оттенок. */
    val coverSeed: Color? = null,
)

@Composable
fun FuzzTheme(
    config: FuzzThemeConfig = FuzzThemeConfig(),
    content: @Composable () -> Unit,
) {
    val dark =
        when (config.mode) {
            ThemeMode.System -> isSystemInDarkTheme()
            ThemeMode.Light -> false
            ThemeMode.Dark, ThemeMode.Amoled -> true
        }
    val amoled = config.mode == ThemeMode.Amoled
    val context = LocalContext.current
    val wallpaper =
        if (config.colorSource == ColorSource.Wallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            null
        }
    val accent =
        when {
            wallpaper != null -> {
                Accent(wallpaper.primary, wallpaper.onPrimary, wallpaper.primaryContainer)
            }

            config.colorSource == ColorSource.Cover && config.coverSeed != null -> {
                accentFor(config.coverSeed.hue(), dark)
            }

            else -> {
                accentFor(BRAND_HUE, dark)
            }
        }
    val scheme = fuzzColorScheme(accent, dark, amoled)
    val extra =
        FuzzColors(
            lamp = if (dark) Neutrals.Dark.Lamp else Neutrals.Light.Lamp,
            onLamp = Neutrals.OnLamp,
            like = if (dark) Neutrals.Dark.Like else Neutrals.Light.Like,
            accentSoft = accent.accentSoft,
            line = scheme.outlineVariant,
        )
    CompositionLocalProvider(LocalFuzzColors provides extra) {
        MaterialTheme(
            colorScheme = scheme,
            typography = FuzzTypography,
            shapes = FuzzShapes,
            content = content,
        )
    }
}

/** Доступ к цветам бренда вне Material: `FuzzTheme.colors.lamp`. */
object FuzzTheme {
    val colors: FuzzColors
        @Composable @ReadOnlyComposable
        get() = LocalFuzzColors.current
}

/**
 * Глубина задаётся тоном поверхности, без теней: фон → поверхность 1 (панель, нижняя навигация)
 * → поверхность 2 (мини-плеер, поле поиска).
 */
internal fun fuzzColorScheme(
    accent: Accent,
    dark: Boolean,
    amoled: Boolean,
): ColorScheme = if (dark) darkScheme(accent, amoled) else lightScheme(accent)

private fun lightScheme(accent: Accent): ColorScheme {
    val n = Neutrals.Light
    return lightColorScheme(
        primary = accent.accent,
        onPrimary = accent.onAccent,
        primaryContainer = accent.accentSoft,
        onPrimaryContainer = accent.accent,
        secondary = accent.accent,
        onSecondary = accent.onAccent,
        secondaryContainer = accent.accentSoft,
        onSecondaryContainer = accent.accent,
        tertiary = n.Lamp,
        onTertiary = Neutrals.OnLamp,
        background = n.Background,
        onBackground = n.Foreground,
        surface = n.Background,
        onSurface = n.Foreground,
        surfaceVariant = n.Surface2,
        onSurfaceVariant = n.ForegroundVariant,
        surfaceContainerLowest = n.Background,
        surfaceContainerLow = n.Surface1,
        surfaceContainer = n.Surface1,
        surfaceContainerHigh = n.Surface2,
        surfaceContainerHighest = n.Surface2,
        inverseSurface = n.Foreground,
        inverseOnSurface = n.Background,
        outline = n.ForegroundVariant,
        outlineVariant = n.Line,
        error = n.Like,
    )
}

private fun darkScheme(
    accent: Accent,
    amoled: Boolean,
): ColorScheme {
    val n = Neutrals.Dark
    val bg = if (amoled) Neutrals.Amoled.Background else n.Background
    val s1 = if (amoled) Neutrals.Amoled.Surface1 else n.Surface1
    val s2 = if (amoled) Neutrals.Amoled.Surface2 else n.Surface2
    val line = if (amoled) Neutrals.Amoled.Line else n.Line
    return darkColorScheme(
        primary = accent.accent,
        onPrimary = accent.onAccent,
        primaryContainer = accent.accentSoft,
        onPrimaryContainer = accent.accent,
        secondary = accent.accent,
        onSecondary = accent.onAccent,
        secondaryContainer = accent.accentSoft,
        onSecondaryContainer = accent.accent,
        tertiary = n.Lamp,
        onTertiary = Neutrals.OnLamp,
        background = bg,
        onBackground = n.Foreground,
        surface = bg,
        onSurface = n.Foreground,
        surfaceVariant = s2,
        onSurfaceVariant = n.ForegroundVariant,
        surfaceContainerLowest = bg,
        surfaceContainerLow = s1,
        surfaceContainer = s1,
        surfaceContainerHigh = s2,
        surfaceContainerHighest = s2,
        inverseSurface = n.Foreground,
        inverseOnSurface = bg,
        outline = n.ForegroundVariant,
        outlineVariant = line,
        error = n.Like,
    )
}

/** Оттенок цвета в градусах 0..360. */
internal fun Color.hue(): Float {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    if (delta == 0f) return BRAND_HUE
    val h =
        when (max) {
            red -> ((green - blue) / delta).mod(SIX)
            green -> (blue - red) / delta + 2f
            else -> (red - green) / delta + 4f
        }
    return h * DEGREES_PER_SECTOR
}

private const val SIX = 6f
private const val DEGREES_PER_SECTOR = 60f
