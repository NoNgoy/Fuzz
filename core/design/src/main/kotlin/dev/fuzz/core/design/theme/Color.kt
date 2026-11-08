package dev.fuzz.core.design.theme

import androidx.compose.ui.graphics.Color

/**
 * Утверждённая палитра Fuzz («Дизайн Fuzz», вкладка «Дизайн-система»): ультрамарин бренда и «лампа»
 * от перегруженной волны. Нейтрали фиксированы, акцент строится из оттенка (бренд, обои или обложка).
 */
internal object Neutrals {
    object Light {
        val Background = Color(0xFFFAFAFF)
        val Surface1 = Color(0xFFF0F0FA)
        val Surface2 = Color(0xFFE4E4F3)
        val Foreground = Color(0xFF14142B)
        val ForegroundVariant = Color(0xFF5A5A7A)
        val Line = Color(0xFFDCDCEC)
        val Like = Color(0xFFE5384F)
        val Lamp = Color(0xFFF5B800)
    }

    object Dark {
        val Background = Color(0xFF0F0F1C)
        val Surface1 = Color(0xFF181828)
        val Surface2 = Color(0xFF232338)
        val Foreground = Color(0xFFF1F1FA)
        val ForegroundVariant = Color(0xFFA3A3C2)
        val Line = Color(0xFF2C2C44)
        val Like = Color(0xFFFF6B7E)
        val Lamp = Color(0xFFFFD23F)
    }

    object Amoled {
        val Background = Color(0xFF000000)
        val Surface1 = Color(0xFF0B0B13)
        val Surface2 = Color(0xFF16161F)
        val Line = Color(0xFF20202C)
    }

    /** Текст на «лампе» (метка «Превью 30 с»). */
    val OnLamp = Color(0xFF2A2200)
}

/** Оттенок и светлота акцента бренда: hsl(244 85% 54%). */
internal const val BRAND_HUE = 244f
internal const val BRAND_LIGHTNESS = 0.54f

/** Акцент и его производные для заданного оттенка, по формулам из макетов. */
internal data class Accent(
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
)

internal fun accentFor(
    hue: Float,
    dark: Boolean,
    lightness: Float = BRAND_LIGHTNESS,
): Accent =
    if (dark) {
        Accent(
            accent = Color.hsl(hue, 0.95f, 0.74f),
            onAccent = Color.hsl(hue, 0.60f, 0.12f),
            accentSoft = Color.hsl(hue, 0.38f, 0.24f),
        )
    } else {
        Accent(
            accent = Color.hsl(hue, 0.85f, lightness),
            onAccent = Color.White,
            accentSoft = Color.hsl(hue, 0.80f, 0.91f),
        )
    }

/** Почти чёрный текст бренда на белых кнопках поверх градиентов. */
val Onyx = Color(0xFF14142B)
