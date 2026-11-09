package dev.fuzz.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.fuzz.core.design.theme.Spacing
import dev.fuzz.core.domain.player.SleepTimerState
import dev.fuzz.core.ui.component.formatDuration

/** Варианты таймера, минуты. */
internal val SleepPresets = listOf(15, 30, 45, 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SleepSheet(
    sleep: SleepTimerState,
    onIntent: (PlayerIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        SleepOptions(sleep = sleep, onPick = { intent ->
            onIntent(intent)
            onDismiss()
        })
    }
}

@Composable
internal fun SleepOptions(
    sleep: SleepTimerState,
    onPick: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(bottom = Spacing.l)) {
        Column(Modifier.padding(horizontal = Spacing.screen)) {
            Text(stringResource(R.string.feature_player_sleep_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                sleepStatus(sleep) ?: stringResource(R.string.feature_player_sleep_hint),
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (sleep == SleepTimerState.Off) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
            )
        }
        Spacer(Modifier.height(Spacing.s))
        SleepPresets.forEach { minutes ->
            Option(
                icon = Icons.Rounded.Timer,
                text = stringResource(R.string.feature_player_sleep_minutes, minutes),
                onClick = { onPick(PlayerIntent.StartSleep(minutes)) },
            )
        }
        Option(
            icon = Icons.Rounded.MusicNote,
            text = stringResource(R.string.feature_player_sleep_end_of_track),
            selected = sleep == SleepTimerState.EndOfTrack,
            onClick = { onPick(PlayerIntent.SleepAfterTrack) },
        )
        if (sleep != SleepTimerState.Off) {
            Option(
                icon = Icons.Rounded.Close,
                text = stringResource(R.string.feature_player_sleep_off),
                onClick = { onPick(PlayerIntent.CancelSleep) },
            )
        }
    }
}

/** Строка о том, что таймер уже идёт, или null, если он выключен. */
@Composable
internal fun sleepStatus(sleep: SleepTimerState): String? =
    when (sleep) {
        SleepTimerState.Off -> {
            null
        }

        is SleepTimerState.Countdown -> {
            stringResource(
                R.string.feature_player_sleep_left,
                formatDuration(sleep.remainingMs),
            )
        }

        SleepTimerState.EndOfTrack -> {
            stringResource(R.string.feature_player_sleep_after_track)
        }
    }

/** Короткая подпись под кнопкой «Сон» в плеере. */
@Composable
internal fun sleepActionLabel(sleep: SleepTimerState): String =
    when (sleep) {
        SleepTimerState.Off -> stringResource(R.string.feature_player_action_sleep)
        is SleepTimerState.Countdown -> formatDuration(sleep.remainingMs)
        SleepTimerState.EndOfTrack -> stringResource(R.string.feature_player_sleep_track_short)
    }

@Composable
private fun Option(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    ListItem(
        headlineContent = { Text(text) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent =
            if (selected) {
                { Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            } else {
                null
            },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 6.dp),
    )
}
