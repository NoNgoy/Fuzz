package dev.fuzz.core.design.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Плитка жанра или настроения: градиент между двумя оттенками и полупрозрачное кольцо в углу. */
@Composable
fun GradientTile(
    text: String,
    hueFrom: Float,
    hueTo: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .height(76.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(
                    Brush.linearGradient(
                        listOf(Color.hsl(hueFrom, 0.70f, 0.42f), Color.hsl(hueTo, 0.65f, 0.26f)),
                    ),
                ).clickable(onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = 31.dp.toPx()
            drawCircle(
                color = Color.White.copy(alpha = RING_ALPHA),
                radius = r - 4.dp.toPx(),
                center = Offset(size.width + 14.dp.toPx() - r, size.height + 18.dp.toPx() - r),
                style = Stroke(width = 8.dp.toPx()),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(12.dp),
        )
    }
}

private const val RING_ALPHA = 0.2f
