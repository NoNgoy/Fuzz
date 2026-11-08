package dev.fuzz.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fuzz.core.design.theme.FuzzTheme
import dev.fuzz.core.design.theme.MonoLabel

enum class BadgeKind { Neutral, Accent, Lamp }

/** Метка источника или формата: FLAC, Jamendo, «Превью 30 с». Пишется словом, а не только цветом. */
@Composable
fun FuzzBadge(
    text: String,
    modifier: Modifier = Modifier,
    kind: BadgeKind = BadgeKind.Neutral,
) {
    val (bg, fg) =
        when (kind) {
            BadgeKind.Neutral -> {
                MaterialTheme.colorScheme.surfaceContainerHigh to
                    MaterialTheme.colorScheme.onSurfaceVariant
            }

            BadgeKind.Accent -> {
                FuzzTheme.colors.accentSoft to MaterialTheme.colorScheme.primary
            }

            BadgeKind.Lamp -> {
                FuzzTheme.colors.lamp to FuzzTheme.colors.onLamp
            }
        }
    Text(
        text = text,
        style = MonoLabel.copy(fontSize = 10.sp, lineHeight = 10.sp),
        color = fg,
        modifier =
            modifier
                .background(bg, RoundedCornerShape(5.dp))
                .padding(horizontal = 5.dp, vertical = 3.dp),
    )
}
