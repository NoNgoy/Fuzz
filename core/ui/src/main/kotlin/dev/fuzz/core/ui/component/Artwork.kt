package dev.fuzz.core.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import coil3.compose.SubcomposeAsyncImage
import dev.fuzz.core.design.component.ArtworkPlaceholder

/** Обложка с заглушкой: пока картинка грузится или её нет, показывается нота. */
@Composable
fun Artwork(
    uri: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
) {
    if (uri == null) {
        ArtworkPlaceholder(modifier = modifier, shape = shape)
        return
    }
    SubcomposeAsyncImage(
        model = uri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(shape),
        loading = { ArtworkPlaceholder(shape = shape) },
        error = { ArtworkPlaceholder(shape = shape) },
    )
}
