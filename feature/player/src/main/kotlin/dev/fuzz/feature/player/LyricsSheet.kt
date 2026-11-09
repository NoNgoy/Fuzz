package dev.fuzz.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fuzz.core.design.theme.Spacing

/** Шторка текста песни. Синхронный текст подсвечивает звучащую строку и сам прокручивается. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsSheet(
    onDismiss: () -> Unit,
    viewModel: LyricsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        LyricsContentView(state, viewModel::onIntent, Modifier.fillMaxHeight(SHEET_HEIGHT))
    }
}

@Composable
internal fun LyricsContentView(
    state: LyricsState,
    onIntent: (LyricsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = Spacing.screen)) {
            Text(
                state.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                state.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        when (val content = state.content) {
            LyricsContent.Loading -> {
                Box(Modifier.fillMaxWidth().padding(Spacing.xxl), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            LyricsContent.Idle -> {
                Message(stringResource(R.string.feature_player_lyrics_idle))
            }

            LyricsContent.NotFound -> {
                Message(stringResource(R.string.feature_player_lyrics_not_found))
            }

            LyricsContent.Offline -> {
                Message(stringResource(R.string.feature_player_lyrics_offline))
            }

            is LyricsContent.Text -> {
                Lines(content, state.currentLine, onIntent)
            }
        }
    }
}

@Composable
private fun Lines(
    content: LyricsContent.Text,
    current: Int,
    onIntent: (LyricsIntent) -> Unit,
) {
    val list = rememberLazyListState()
    LaunchedEffect(current) {
        if (current >= 0) list.animateScrollToItem((current - LINES_ABOVE).coerceAtLeast(0))
    }
    LazyColumn(state = list, contentPadding = PaddingValues(vertical = Spacing.l)) {
        itemsIndexed(content.lines) { index, line ->
            val active = !content.synced || index == current
            Text(
                line.text.ifBlank { "♪" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = if (index == current) FontWeight.Bold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = content.synced) { onIntent(LyricsIntent.SeekToLine(index)) }
                        .padding(horizontal = Spacing.screen, vertical = Spacing.xs),
            )
        }
        item {
            Text(
                stringResource(R.string.feature_player_lyrics_source, content.source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.l),
            )
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(Spacing.screen),
    )
}

private const val SHEET_HEIGHT = 0.9f
private const val LINES_ABOVE = 2
