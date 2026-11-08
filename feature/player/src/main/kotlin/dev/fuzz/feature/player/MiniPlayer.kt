package dev.fuzz.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fuzz.core.design.theme.Spacing
import dev.fuzz.core.model.PlaybackState
import dev.fuzz.core.ui.component.Artwork

/** Мини-плеер над нижней панелью. Появляется, когда в очереди есть трек. */
@Composable
fun MiniPlayer(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MiniPlayerContent(
        playback = state.playback,
        onExpand = onExpand,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

@Composable
internal fun MiniPlayerContent(
    playback: PlaybackState,
    onExpand: () -> Unit,
    onIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = playback.currentTrack
    AnimatedVisibility(visible = track != null, modifier = modifier) {
        if (track == null) return@AnimatedVisibility
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
            Column {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onExpand)
                            .padding(horizontal = Spacing.m, vertical = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(uri = track.artworkUri, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.width(Spacing.m))
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            track.artistName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onIntent(PlayerIntent.TogglePlay) }) {
                        Icon(
                            imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(playPauseLabel(playback.isPlaying)),
                        )
                    }
                    IconButton(onClick = { onIntent(PlayerIntent.Next) }, enabled = playback.hasNext) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.feature_player_next))
                    }
                }
                LinearProgressIndicator(
                    progress = { playback.progress },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
                    drawStopIndicator = {},
                )
            }
        }
    }
}

internal fun playPauseLabel(isPlaying: Boolean): Int =
    if (isPlaying) R.string.feature_player_pause else R.string.feature_player_play
