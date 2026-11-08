package dev.fuzz.feature.player

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.fuzz.core.design.theme.Spacing
import dev.fuzz.core.model.PlaybackState
import dev.fuzz.core.ui.component.TrackRow

/** Шторка очереди: текущий трек подсвечен, тап переключает на трек. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSheet(
    playback: PlaybackState,
    onPlay: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Text(
            stringResource(R.string.feature_player_action_queue),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = Spacing.screen),
        )
        Text(
            stringResource(R.string.feature_player_queue_count, playback.queue.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs),
        )
        LazyColumn {
            itemsIndexed(playback.queue, key = { i, t -> "$i:${t.id.value}" }) { index, track ->
                TrackRow(track = track, isCurrent = index == playback.currentIndex, onClick = { onPlay(index) })
            }
        }
    }
}
