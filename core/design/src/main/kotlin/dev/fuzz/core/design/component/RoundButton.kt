package dev.fuzz.core.design.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Круглая кнопка-переключатель 44 dp (лайк, дизлайк). */
@Composable
fun RoundToggleButton(
    icon: ImageVector,
    contentDescription: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    checkedContainer: Color,
    checkedContent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Surface(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shape = CircleShape,
        color = if (checked) checkedContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (checked) checkedContent else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription)
        }
    }
}
