package dev.fuzz.core.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Цвета бренда, которых нет в ролях Material 3. */
@Immutable
data class FuzzColors(
    val lamp: Color,
    val onLamp: Color,
    val like: Color,
    val accentSoft: Color,
    val line: Color,
)

internal val LocalFuzzColors =
    staticCompositionLocalOf {
        FuzzColors(
            lamp = Neutrals.Dark.Lamp,
            onLamp = Neutrals.OnLamp,
            like = Neutrals.Dark.Like,
            accentSoft = Color.Unspecified,
            line = Neutrals.Dark.Line,
        )
    }
