package dev.fuzz.core.design.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale

/** Знак Fuzz: волна, срезанная сверху и снизу, срезы цвета «лампа». Нарисован в сетке 96. */
@Composable
fun FuzzLogo(
    background: Color,
    wave: Color,
    lamp: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        scale(size.minDimension / GRID, pivot = Offset.Zero) {
            drawRoundRect(background, cornerRadius = CornerRadius(RADIUS))
            val stroke = Stroke(width = STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(wavePath(), wave, style = stroke)
            drawPath(clipPath(), lamp, style = stroke)
        }
    }
}

private fun wavePath() =
    Path().apply {
        moveTo(10f, 52f)
        lineTo(22f, 52f)
        lineTo(29f, 30f)
        moveTo(43f, 30f)
        lineTo(55f, 66f)
        moveTo(67f, 66f)
        lineTo(74f, 52f)
        lineTo(86f, 52f)
    }

private fun clipPath() =
    Path().apply {
        moveTo(29f, 30f)
        lineTo(43f, 30f)
        moveTo(55f, 66f)
        lineTo(67f, 66f)
    }

private const val GRID = 96f
private const val RADIUS = 26f
private const val STROKE = 7f
