package dev.fuzz.feature.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fuzz.core.design.component.ScreenTitle
import dev.fuzz.core.design.theme.FuzzTheme
import dev.fuzz.core.design.theme.Spacing
import dev.fuzz.core.domain.importer.ImportItem
import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.core.ui.component.NameDialog
import dev.fuzz.core.ui.component.TrackRow
import dev.fuzz.core.ui.component.formatDuration
import dev.fuzz.core.ui.mvi.CollectEffects

@Composable
internal fun ImportEntry(
    onBack: () -> Unit,
    onSaved: (playlistId: String) -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is ImportEffect.Saved -> {
                val text = resources.getString(R.string.feature_import_saved, effect.name)
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                onSaved(effect.playlistId.value)
            }
        }
    }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                viewModel.onIntent(
                    ImportIntent.FilePicked(context.displayName(uri)) { context.contentResolver.openInputStream(uri) },
                )
            }
        }
    // Расширения у плейлистов MIME-типами описаны кто во что горазд, поэтому принимаем всё и смотрим на имя файла.
    ImportScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onPick = { picker.launch(arrayOf("*/*")) },
        onBack = onBack,
    )
}

private fun Context.displayName(uri: Uri): String =
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: uri.lastPathSegment.orEmpty()

@Composable
internal fun ImportScreen(
    state: ImportState,
    onIntent: (ImportIntent) -> Unit,
    onPick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.feature_import_back),
                )
            }
            Text(
                stringResource(R.string.feature_import_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (val stage = state.stage) {
            ImportStage.PickFile -> {
                PickFile(state, onPick)
            }

            ImportStage.Reading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }

            is ImportStage.Report -> {
                Report(stage, onIntent)
            }
        }
    }
}

@Composable
private fun PickFile(
    state: ImportState,
    onPick: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        ScreenTitle(stringResource(R.string.feature_import_pick_title))
        Text(
            stringResource(R.string.feature_import_pick_body, state.supportedFormats),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.problem?.let { problem ->
            Text(
                when (problem) {
                    is ImportProblem.Unsupported -> {
                        stringResource(
                            R.string.feature_import_unsupported,
                            problem.extension.ifEmpty { "?" },
                            problem.supported,
                        )
                    }

                    ImportProblem.Unreadable -> {
                        stringResource(R.string.feature_import_unreadable)
                    }

                    ImportProblem.Empty -> {
                        stringResource(R.string.feature_import_empty)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(onClick = onPick, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.Rounded.FileOpen, contentDescription = null)
            Text(stringResource(R.string.feature_import_pick), Modifier.padding(start = Spacing.s))
        }
        Text(
            stringResource(R.string.feature_import_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Report(
    report: ImportStage.Report,
    onIntent: (ImportIntent) -> Unit,
) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    val saveCount = report.count(ReportTab.Found)
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = Spacing.l)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        ScreenTitle(report.name)
                        Text(
                            "${report.format} · " +
                                pluralStringResource(
                                    R.plurals.feature_import_tracks,
                                    report.rows.size,
                                    report.rows.size,
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { renaming = true }) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.feature_import_rename))
                    }
                }
            }
            item { Tabs(report, onIntent) }
            val pending = report.nextPending
            if (report.tab == ReportTab.Doubtful && pending >= 0) {
                item { Confirm(pending, report.rows[pending], report.count(ReportTab.Doubtful), onIntent) }
            }
            val rows = report.rows.withIndex().filter { it.value.tab == report.tab }
            if (rows.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.feature_import_tab_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.screen),
                    )
                }
            }
            items(rows, key = { it.index }) { (index, row) -> RowItem(index, row, onIntent) }
        }
        Button(
            onClick = { onIntent(ImportIntent.Save) },
            enabled = saveCount > 0 && !report.saving,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing.screen).height(48.dp),
        ) {
            Text(pluralStringResource(R.plurals.feature_import_save, saveCount, saveCount))
        }
    }
    if (renaming) {
        NameDialog(
            title = stringResource(R.string.feature_import_rename),
            initial = report.name,
            onConfirm = {
                renaming = false
                onIntent(ImportIntent.Rename(it))
            },
            onDismiss = { renaming = false },
        )
    }
}

@Composable
private fun Tabs(
    report: ImportStage.Report,
    onIntent: (ImportIntent) -> Unit,
) {
    Row(
        Modifier
            .padding(horizontal = Spacing.screen, vertical = Spacing.m)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium),
    ) {
        ReportTab.entries.forEach { tab ->
            val selected = tab == report.tab
            Column(
                Modifier
                    .weight(1f)
                    .background(
                        if (selected) FuzzTheme.colors.accentSoft else MaterialTheme.colorScheme.surfaceContainer,
                        MaterialTheme.shapes.medium,
                    )
                    .clickable { onIntent(ImportIntent.SelectTab(tab)) }
                    .padding(vertical = Spacing.s),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val color =
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Text(report.count(tab).toString(), style = MaterialTheme.typography.titleMedium, color = color)
                Text(stringResource(tab.label), style = MaterialTheme.typography.labelMedium, color = color)
            }
        }
    }
}

private val ReportTab.label: Int
    get() =
        when (this) {
            ReportTab.Found -> R.string.feature_import_found
            ReportTab.Doubtful -> R.string.feature_import_doubtful
            ReportTab.NotFound -> R.string.feature_import_not_found
        }

@Composable
private fun Confirm(
    index: Int,
    row: ReportRow,
    left: Int,
    onIntent: (ImportIntent) -> Unit,
) {
    val item = row.item as ImportItem.Doubtful
    Column(
        Modifier
            .padding(horizontal = Spacing.screen, vertical = Spacing.s)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            stringResource(R.string.feature_import_confirm, left),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = Spacing.l),
        )
        Text(
            stringResource(R.string.feature_import_in_list, item.ref.display()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.l),
        )
        item.candidates.forEach { candidate ->
            TrackRow(
                track = candidate,
                onClick = { onIntent(ImportIntent.Confirm(index, candidate)) },
                trailing = {
                    TextButton(onClick = { onIntent(ImportIntent.Confirm(index, candidate)) }) {
                        Text(stringResource(R.string.feature_import_this_one))
                    }
                },
            )
        }
        OutlinedButton(
            onClick = { onIntent(ImportIntent.Reject(index)) },
            modifier = Modifier.padding(horizontal = Spacing.l),
        ) { Text(stringResource(R.string.feature_import_none)) }
    }
}

@Composable
private fun RowItem(
    index: Int,
    row: ReportRow,
    onIntent: (ImportIntent) -> Unit,
) {
    val track = row.track
    if (track != null) {
        TrackRow(track = track, onClick = {})
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.item.ref.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                row.item.ref.artist.ifEmpty { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Отклонённую по ошибке строку можно вернуть к выбору кандидатов.
        if (row.rejected) {
            TextButton(onClick = { onIntent(ImportIntent.Restore(index)) }) {
                Text(stringResource(R.string.feature_import_restore))
            }
        }
    }
}

private fun TrackRef.display(): String {
    val duration = durationMs?.let { ", ${formatDuration(it)}" }.orEmpty()
    return listOf(artist, title).filter { it.isNotEmpty() }.joinToString(" — ") + duration
}
