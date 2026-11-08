package dev.fuzz.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.fuzz.core.design.component.WaveBars
import dev.fuzz.core.design.theme.FuzzTheme
import dev.fuzz.core.design.theme.Onyx
import dev.fuzz.core.design.theme.Spacing

/** Большая карточка «Моя волна» на главной: столбики пульсируют, пока играет музыка. */
@Composable
internal fun WaveCard(
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hue = FuzzTheme.colors.accentHue
    val gradient =
        Brush.linearGradient(
            listOf(Color.hsl(hue, 0.85f, 0.44f), Color.hsl((hue + HUE_SHIFT) % FULL_CIRCLE, 0.80f, 0.32f)),
        )
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(gradient)
                .padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Tag(stringResource(R.string.feature_home_wave_tag))
            Tag(stringResource(R.string.feature_home_wave_mix, DEFAULT_NEW_SHARE), ghost = true)
        }
        WaveBars(
            playing = isPlaying,
            barColor = Color.White.copy(alpha = BAR_ALPHA),
            peakColor = FuzzTheme.colors.lamp,
            modifier = Modifier.fillMaxWidth().height(78.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.feature_home_wave_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                Text(
                    stringResource(R.string.feature_home_wave_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = SUBTITLE_ALPHA),
                )
            }
            FilledIconButton(
                onClick = onPlay,
                modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Onyx),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription =
                        stringResource(
                            if (isPlaying) R.string.feature_home_wave_pause else R.string.feature_home_wave_play,
                        ),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                R.string.feature_home_wave_activity,
                R.string.feature_home_wave_mood,
                R.string.feature_home_wave_character,
            )
                .forEach { label -> WaveChip(text = stringResource(label), onClick = onOpenSettings) }
            Surface(
                onClick = onOpenSettings,
                shape = CircleShape,
                color = Color.White.copy(alpha = CHIP_ALPHA),
                contentColor = Color.White,
            ) {
                Icon(
                    Icons.Rounded.Tune,
                    contentDescription = stringResource(R.string.feature_home_wave_settings),
                    modifier = Modifier.padding(6.dp).size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun Tag(
    text: String,
    ghost: Boolean = false,
) {
    val shape = RoundedCornerShape(percent = 50)
    Surface(
        shape = shape,
        color = if (ghost) Color.Transparent else Color.White.copy(alpha = TAG_ALPHA),
        contentColor = Color.White,
        border = if (ghost) BorderStroke(1.dp, Color.White.copy(alpha = GHOST_ALPHA)) else null,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun WaveChip(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = Color.White.copy(alpha = CHIP_ALPHA),
        contentColor = Color.White,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/** Положение ползунка «Знакомое ↔ Новое» по умолчанию. */
private const val DEFAULT_NEW_SHARE = 40
private const val HUE_SHIFT = 70f
private const val FULL_CIRCLE = 360f
private const val BAR_ALPHA = 0.85f
private const val SUBTITLE_ALPHA = 0.85f
private const val TAG_ALPHA = 0.18f
private const val GHOST_ALPHA = 0.4f
private const val CHIP_ALPHA = 0.16f
