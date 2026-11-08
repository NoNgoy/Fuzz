package dev.fuzz.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fuzz.core.design.component.FuzzBadge
import dev.fuzz.core.design.component.RoundToggleButton
import dev.fuzz.core.design.theme.FuzzTheme
import dev.fuzz.core.design.theme.MonoLabel
import dev.fuzz.core.design.theme.Spacing
import dev.fuzz.core.model.PlaybackState
import dev.fuzz.core.model.Reaction
import dev.fuzz.core.model.RepeatMode
import dev.fuzz.core.ui.component.Artwork
import dev.fuzz.core.ui.component.formatDuration
import dev.fuzz.core.ui.mvi.CollectEffects
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun PlayerScreen(
    onCollapse: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showQueue by remember { mutableStateOf(false) }
    val upcoming = upcomingMessages()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is PlayerEffect.ShowUpcoming -> snackbar.showSnackbar(upcoming.getValue(effect.feature))
        }
    }
    Box {
        PlayerContent(
            state = state,
            onCollapse = onCollapse,
            onIntent = viewModel::onIntent,
            onOpenQueue = { showQueue = true },
        )
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding())
    }
    if (showQueue) {
        QueueSheet(
            playback = state.playback,
            onPlay = { viewModel.onIntent(PlayerIntent.PlayQueueItem(it)) },
            onDismiss = { showQueue = false },
        )
    }
}

@Composable
private fun upcomingMessages(): Map<UpcomingFeature, String> =
    mapOf(
        UpcomingFeature.Lyrics to stringResource(R.string.feature_player_soon_lyrics),
        UpcomingFeature.Why to stringResource(R.string.feature_player_soon_why),
        UpcomingFeature.Sound to stringResource(R.string.feature_player_soon_sound),
        UpcomingFeature.Sleep to stringResource(R.string.feature_player_soon_sleep),
    )

@Composable
internal fun PlayerContent(
    state: PlayerUiState,
    onCollapse: () -> Unit,
    onIntent: (PlayerIntent) -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playback = state.playback
    val track = playback.currentTrack
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(onCollapse)
            Spacer(Modifier.height(10.dp))
            Cover(
                artworkUri = track?.artworkUri,
                description = track?.let { stringResource(R.string.feature_player_cover, it.title, it.artistName) },
                onNext = { onIntent(PlayerIntent.Next) },
                onPrevious = { onIntent(PlayerIntent.Previous) },
                onLike = { onIntent(PlayerIntent.Like) },
            )
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = track?.title.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = track?.artistName.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        track?.format?.let { FuzzBadge(it) }
                    }
                }
                RoundToggleButton(
                    icon =
                        if (state.reaction ==
                            Reaction.Dislike
                        ) {
                            Icons.Rounded.ThumbDown
                        } else {
                            Icons.Outlined.ThumbDown
                        },
                    contentDescription = stringResource(R.string.feature_player_dislike),
                    checked = state.reaction == Reaction.Dislike,
                    onCheckedChange = { onIntent(PlayerIntent.ToggleDislike) },
                    checkedContainer = MaterialTheme.colorScheme.onSurface,
                    checkedContent = MaterialTheme.colorScheme.surface,
                )
                RoundToggleButton(
                    icon =
                        if (state.reaction ==
                            Reaction.Like
                        ) {
                            Icons.Rounded.Favorite
                        } else {
                            Icons.Rounded.FavoriteBorder
                        },
                    contentDescription = stringResource(R.string.feature_player_like),
                    checked = state.reaction == Reaction.Like,
                    onCheckedChange = { onIntent(PlayerIntent.ToggleLike) },
                    checkedContainer = FuzzTheme.colors.like,
                    checkedContent = Color.White,
                )
            }
            Spacer(Modifier.height(Spacing.s))
            SeekBar(playback = playback, onSeek = { onIntent(PlayerIntent.Seek(it)) })
            Controls(playback = playback, onIntent = onIntent)
            Spacer(Modifier.height(6.dp))
            Actions(onIntent = onIntent, onOpenQueue = onOpenQueue)
        }
    }
}

@Composable
private fun TopBar(onCollapse: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCollapse) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.feature_player_collapse))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.feature_player_playing_from).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.feature_player_source_collection), style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.size(48.dp))
    }
}

/** Обложка: свайп влево и вправо листает треки, двойной тап ставит лайк с сердцем поверх. */
@Composable
private fun Cover(
    artworkUri: String?,
    description: String?,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onLike: () -> Unit,
) {
    val threshold = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }
    var drag by remember { mutableStateOf(0f) }
    var heart by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val heartAlpha by animateFloatAsState(if (heart) HEART_ALPHA else 0f, tween(HEART_MS), label = "heart")
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer { translationX = drag * DRAG_FOLLOW }
                .semantics { description?.let { contentDescription = it } }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                drag < -threshold -> onNext()
                                drag > threshold -> onPrevious()
                            }
                            drag = 0f
                        },
                        onDragCancel = { drag = 0f },
                    ) { _, amount -> drag += amount }
                }.pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        onLike()
                        heart = true
                        scope.launch {
                            delay(HEART_SHOW_MS)
                            heart = false
                        }
                    })
                },
        contentAlignment = Alignment.Center,
    ) {
        Artwork(uri = artworkUri, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxSize())
        Icon(
            Icons.Rounded.Favorite,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(84.dp).graphicsLayer { alpha = heartAlpha },
        )
    }
}

@Composable
private fun SeekBar(
    playback: PlaybackState,
    onSeek: (Float) -> Unit,
) {
    // Пока палец на ползунке, показываем его положение, а не позицию плеера.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val label = stringResource(R.string.feature_player_seek)
    Slider(
        value = dragging ?: playback.progress,
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            dragging?.let(onSeek)
            dragging = null
        },
        enabled = playback.durationMs > 0,
        colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.semantics { contentDescription = label },
    )
    val shown = dragging?.let { (it * playback.durationMs).toLong() } ?: playback.positionMs
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatDuration(shown), style = MonoLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "−" + formatDuration((playback.durationMs - shown).coerceAtLeast(0)),
            style = MonoLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                stringResource(R.string.feature_player_shuffle),
                tint = toggleTint(playback.shuffle),
            )
        }
        IconButton(onClick = { onIntent(PlayerIntent.Previous) }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.feature_player_previous), Modifier.size(32.dp))
        }
        FilledIconButton(
            onClick = { onIntent(PlayerIntent.TogglePlay) },
            modifier = Modifier.size(68.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(),
        ) {
            Icon(
                imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(playPauseLabel(playback.isPlaying)),
                modifier = Modifier.size(32.dp),
            )
        }
        IconButton(onClick = {
            onIntent(PlayerIntent.Next)
        }, enabled = playback.hasNext, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.SkipNext, stringResource(R.string.feature_player_next), Modifier.size(32.dp))
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
private fun Actions(
    onIntent: (PlayerIntent) -> Unit,
    onOpenQueue: () -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        Action(Icons.AutoMirrored.Rounded.Subject, R.string.feature_player_action_lyrics) {
            onIntent(PlayerIntent.OpenUpcoming(UpcomingFeature.Lyrics))
        }
        Action(Icons.AutoMirrored.Rounded.QueueMusic, R.string.feature_player_action_queue, onOpenQueue)
        Action(Icons.Rounded.AutoAwesome, R.string.feature_player_action_why) {
            onIntent(PlayerIntent.OpenUpcoming(UpcomingFeature.Why))
        }
        Action(Icons.Rounded.Equalizer, R.string.feature_player_action_sound) {
            onIntent(PlayerIntent.OpenUpcoming(UpcomingFeature.Sound))
        }
        Action(Icons.Rounded.Bedtime, R.string.feature_player_action_sleep) {
            onIntent(PlayerIntent.OpenUpcoming(UpcomingFeature.Sleep))
        }
    }
}

@Composable
private fun RowScope.Action(
    icon: ImageVector,
    label: Int,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.weight(1f)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun toggleTint(active: Boolean) =
    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

private val SWIPE_THRESHOLD = 50.dp
private const val DRAG_FOLLOW = 0.4f
private const val HEART_ALPHA = 0.95f
private const val HEART_MS = 350
private const val HEART_SHOW_MS = 600L
