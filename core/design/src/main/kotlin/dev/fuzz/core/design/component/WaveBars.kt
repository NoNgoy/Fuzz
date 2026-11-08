package dev.fuzz.core.design.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import dev.fuzz.core.design.theme.Motion
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Столбики волны. Высоты повторяют знак Fuzz: самые высокие «срезаны» и окрашены в цвет лампы.
 * Пока [playing], столбики пульсируют; на паузе замирают.
 */
@Composable
fun WaveBars(
    playing: Boolean,
    barColor: Color,
    peakColor: Color,
    modifier: Modifier = Modifier,
    count: Int = 30,
) {
    val heights = remember(count) { List(count) { i -> barHeight(i) } }
    // На паузе анимация не создаётся вовсе: столбики стоят и не перерисовываются каждый кадр.
    val phase = if (playing) rememberWavePhase() else null
    Canvas(modifier) {
        val gap = size.width * GAP_FRACTION / count
        val barWidth = (size.width - gap * (count - 1)) / count
        heights.forEachIndexed { i, h ->
            val offset = (i * DELAY_STEP) % 1f
            val local = phase?.let { abs(((it.value + offset) % 1f) * 2f - 1f) } ?: 1f
            val scale = MIN_SCALE + (1f - MIN_SCALE) * local
            val barHeight = size.height * h * scale
            drawRoundRect(
                color = if (h > PEAK) peakColor else barColor,
                topLeft = Offset(i * (barWidth + gap), (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

@Composable
private fun rememberWavePhase(): State<Float> =
    rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(Motion.WAVE_PULSE), RepeatMode.Reverse),
        label = "phase",
    )

private fun barHeight(i: Int): Float {
    val v = BASE + abs(sin(i * FREQ_A) * cos(i * FREQ_B)) * RANGE
    return (v / FULL).toFloat().coerceAtMost(1f)
}

private const val BASE = 28.0
private const val RANGE = 72.0
private const val FULL = 100.0
private const val FREQ_A = 1.7
private const val FREQ_B = 0.6
private const val PEAK = 0.92f
private const val MIN_SCALE = 0.35f
private const val GAP_FRACTION = 0.25f
private const val DELAY_STEP = 0.37f
