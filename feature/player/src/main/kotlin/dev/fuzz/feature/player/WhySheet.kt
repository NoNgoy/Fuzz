package dev.fuzz.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fuzz.core.design.theme.Spacing
import dev.fuzz.core.domain.wave.WaveAnchor
import dev.fuzz.core.domain.wave.WaveFactor
import dev.fuzz.core.domain.wave.WaveReason
import dev.fuzz.core.domain.wave.WaveSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WhySheet(
    onDismiss: () -> Unit,
    viewModel: WhyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        WhyContentView(state)
    }
}

@Composable
internal fun WhyContentView(
    state: WhyState,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(bottom = Spacing.xl)) {
        Text(stringResource(R.string.feature_player_why_title), style = MaterialTheme.typography.headlineSmall)
        if (state.title.isNotEmpty()) {
            Text(
                stringResource(R.string.feature_player_why_track, state.title, state.artist),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when (val content = state.content) {
            WhyContent.Loading -> {
                Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            WhyContent.Idle -> {
                Message(stringResource(R.string.feature_player_lyrics_idle))
            }

            WhyContent.NotWave -> {
                Message(stringResource(R.string.feature_player_why_not_wave))
            }

            is WhyContent.Explained -> {
                Explanation(content.reason)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Explanation(reason: WaveReason) {
    Text(
        headline(reason),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = Spacing.l, bottom = Spacing.m),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        if (reason.anchor != null) Point(Icons.Rounded.Tune, stringResource(reason.factor.text))
        Point(Icons.Rounded.Inventory2, stringResource(reason.source.text))
        reason.bpm?.let { Point(Icons.Rounded.Speed, stringResource(R.string.feature_player_why_bpm, it)) }
    }
    if (reason.tags.isNotEmpty()) {
        Text(
            stringResource(R.string.feature_player_why_tags),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.l),
        )
        // Просто подписи: тег ничего не открывает, поэтому и не похож на кнопку.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
            modifier = Modifier.padding(top = Spacing.s),
        ) {
            reason.tags.forEach { tag ->
                Text(
                    tag,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** Главная строка: через что трек связан со вкусом, а без такой связи — что сильнее всего его подняло. */
@Composable
private fun headline(reason: WaveReason): String {
    val anchor = reason.anchor ?: return stringResource(reason.factor.text)
    val res =
        when (anchor.kind) {
            WaveAnchor.Kind.LikedArtist -> R.string.feature_player_why_anchor_liked_artist
            WaveAnchor.Kind.ListenedArtist -> R.string.feature_player_why_anchor_listened_artist
            WaveAnchor.Kind.LikedTrack -> R.string.feature_player_why_anchor_liked_track
            WaveAnchor.Kind.Seed -> R.string.feature_player_why_anchor_seed
        }
    return stringResource(res, anchor.name)
}

private val WaveFactor.text: Int
    get() =
        when (this) {
            WaveFactor.Tags -> R.string.feature_player_why_factor_tags
            WaveFactor.Artist -> R.string.feature_player_why_factor_artist
            WaveFactor.Sound -> R.string.feature_player_why_factor_sound
            WaveFactor.Mood -> R.string.feature_player_why_factor_mood
            WaveFactor.Novelty -> R.string.feature_player_why_factor_novelty
            WaveFactor.Popularity -> R.string.feature_player_why_factor_popularity
            WaveFactor.Link -> R.string.feature_player_why_factor_link
            WaveFactor.Chance -> R.string.feature_player_why_factor_chance
        }

private val WaveSource.text: Int
    get() =
        when (this) {
            WaveSource.Favorites -> R.string.feature_player_why_source_favorites
            WaveSource.Unheard -> R.string.feature_player_why_source_unheard
            WaveSource.Similar -> R.string.feature_player_why_source_similar
            WaveSource.OpenCatalog -> R.string.feature_player_why_source_open
            WaveSource.Preview -> R.string.feature_player_why_source_preview
        }

@Composable
private fun Point(
    icon: ImageVector,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.l),
    )
}
