package dev.fuzz.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fuzz.core.design.theme.Spacing
import dev.fuzz.core.model.PlaybackState
import dev.fuzz.core.model.RepeatMode
import dev.fuzz.core.ui.component.Artwork
import dev.fuzz.core.ui.component.formatDuration

@Composable
internal fun PlayerScreen(
    onCollapse: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PlayerContent(playback = state.playback, onCollapse = onCollapse, onIntent = viewModel::onIntent)
}

@Composable
internal fun PlayerContent(
    playback: PlaybackState,
    onCollapse: () -> Unit,
    onIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = playback.currentTrack
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(horizontal = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth()) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.feature_player_collapse),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.l))
            Artwork(
                uri = track?.artworkUri,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
            Spacer(Modifier.height(Spacing.xl))
            Text(
                text = track?.title.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = track?.artistName.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.l))
            SeekBar(playback = playback, onSeek = { onIntent(PlayerIntent.Seek(it)) })
            Spacer(Modifier.height(Spacing.m))
            Controls(playback = playback, onIntent = onIntent)
        }
    }
}

@Composable
private fun SeekBar(
    playback: PlaybackState,
    onSeek: (Float) -> Unit,
) {
    // Пока палец на ползунке, показываем его положение, а не позицию плеера.
    var dragging by remember { mutableStateOf<Float?>(null) }
    Slider(
        value = dragging ?: playback.progress,
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            dragging?.let(onSeek)
            dragging = null
        },
        enabled = playback.durationMs > 0,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val shownPosition = dragging?.let { (it * playback.durationMs).toLong() } ?: playback.positionMs
        Text(formatDuration(shownPosition), style = MaterialTheme.typography.labelMedium)
        Text(formatDuration(playback.durationMs), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Controls(
    playback: PlaybackState,
    onIntent: (PlayerIntent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onIntent(PlayerIntent.ToggleShuffle) }) {
            Icon(
                Icons.Rounded.Shuffle,
                contentDescription = stringResource(R.string.feature_player_shuffle),
                tint = toggleTint(playback.shuffle),
            )
        }
        IconButton(onClick = { onIntent(PlayerIntent.Previous) }) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = stringResource(R.string.feature_player_previous),
                modifier = Modifier.size(36.dp),
            )
        }
        FilledIconButton(onClick = { onIntent(PlayerIntent.TogglePlay) }, modifier = Modifier.size(72.dp)) {
            Icon(
                imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription =
                    stringResource(playPauseLabel(playback.isPlaying)),
                modifier = Modifier.size(40.dp),
            )
        }
        IconButton(onClick = { onIntent(PlayerIntent.Next) }, enabled = playback.hasNext) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.feature_player_next),
                modifier = Modifier.size(36.dp),
            )
        }
        IconButton(onClick = { onIntent(PlayerIntent.CycleRepeat) }) {
            Icon(
                imageVector =
                    if (playback.repeatMode ==
                        RepeatMode.One
                    ) {
                        Icons.Rounded.RepeatOne
                    } else {
                        Icons.Rounded.Repeat
                    },
                contentDescription = stringResource(R.string.feature_player_repeat),
                tint = toggleTint(playback.repeatMode != RepeatMode.Off),
            )
        }
    }
}

@Composable
private fun toggleTint(active: Boolean) =
    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
