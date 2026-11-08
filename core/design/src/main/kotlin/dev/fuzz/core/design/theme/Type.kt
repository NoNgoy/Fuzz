package dev.fuzz.core.design.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.fuzz.core.design.R

/** Unbounded: заголовки и крупные цифры. */
val Unbounded =
    FontFamily(
        Font(R.font.unbounded_medium, FontWeight.Medium),
        Font(R.font.unbounded_bold, FontWeight.Bold),
    )

/** Onest: весь интерфейс. */
val Onest =
    FontFamily(
        Font(R.font.onest_regular, FontWeight.Normal),
        Font(R.font.onest_medium, FontWeight.Medium),
        Font(R.font.onest_semibold, FontWeight.SemiBold),
    )

/** JetBrains Mono: время, формат файла, служебные метки. */
val FuzzMono = FontFamily(Font(R.font.jetbrains_mono_medium, FontWeight.Medium))

private val Tight = (-0.02).em

/**
 * Шкала из макета: Display 28/32, заголовок экрана 21/24 (Unbounded 700), раздел 17/22 (Onest 600),
 * основной 15/22, подпись и чип 13/18 (Onest 500), моно 12/16.
 */
internal val FuzzTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = Unbounded,
                fontWeight = FontWeight.Bold,
                fontSize = 44.sp,
                lineHeight = 48.sp,
                letterSpacing = Tight,
            ),
        displayMedium =
            TextStyle(
                fontFamily = Unbounded,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                lineHeight = 40.sp,
                letterSpacing = Tight,
            ),
        displaySmall =
            TextStyle(
                fontFamily = Unbounded,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 32.sp,
                letterSpacing = Tight,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = Unbounded,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                letterSpacing = Tight,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = Unbounded,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
                lineHeight = 24.sp,
                letterSpacing = Tight,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = Unbounded,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                lineHeight = 23.sp,
                letterSpacing = Tight,
            ),
        titleLarge =
            TextStyle(
                fontFamily = Onest,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                lineHeight = 22.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = Onest,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 21.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = Onest,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 19.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = Onest,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = Onest,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = Onest,
                fontWeight = FontWeight.Normal,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = Onest,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = Onest,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = Onest,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            ),
    )

/** Моноширинный стиль для времени и меток формата. */
val MonoLabel = TextStyle(fontFamily = FuzzMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
