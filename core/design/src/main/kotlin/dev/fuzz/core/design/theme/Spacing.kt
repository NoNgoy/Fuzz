package dev.fuzz.core.design.theme

import androidx.compose.ui.unit.dp

/** Сетка 4 dp: поля экрана 16, между секциями 18, цель касания не меньше 48. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val section = 18.dp
    val xl = 24.dp
    val xxl = 32.dp
    val screen = 16.dp
    val touchTarget = 48.dp
}

/** Длительности анимаций из макета, мс. */
object Motion {
    const val PRESS = 120
    const val SHEET = 300
    const val ACCENT = 400
    const val WAVE_PULSE = 1100
}
