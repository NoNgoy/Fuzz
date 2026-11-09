package dev.fuzz.feature.importer

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fuzz.core.domain.importer.ImportItem
import dev.fuzz.core.domain.importer.ImportOutcome
import dev.fuzz.core.domain.importer.ImportPlaylistUseCase
import dev.fuzz.core.domain.importer.SaveImportedPlaylistUseCase
import dev.fuzz.core.domain.player.PlayerController
import dev.fuzz.core.domain.wishlist.WishReason
import dev.fuzz.core.domain.wishlist.WishRequest
import dev.fuzz.core.model.PlaylistId
import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId
import dev.fuzz.core.ui.mvi.MviViewModel
import dev.fuzz.core.ui.mvi.UiEffect
import dev.fuzz.core.ui.mvi.UiIntent
import dev.fuzz.core.ui.mvi.UiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

enum class ReportTab { Found, Doubtful, NotFound }

/** Строка отчёта: что было в файле, что нашли и что решил пользователь. */
data class ReportRow(
    val item: ImportItem,
    /** Для сомнительных: выбранный кандидат или null, пока не решено; [rejected] — «нет в коллекции». */
    val chosen: Track? = null,
    val rejected: Boolean = false,
) {
    val track: Track? get() = (item as? ImportItem.Found)?.track ?: chosen
    val pending: Boolean get() = item is ImportItem.Doubtful && chosen == null && !rejected
    val tab: ReportTab
        get() =
            when {
                track != null -> ReportTab.Found
                item is ImportItem.Doubtful && !rejected -> ReportTab.Doubtful
                else -> ReportTab.NotFound
            }
}

sealed interface ImportStage {
    data object PickFile : ImportStage

    data object Reading : ImportStage

    data class Report(
        val name: String,
        val format: String,
        val rows: ImmutableList<ReportRow>,
        val tab: ReportTab,
        val saving: Boolean = false,
    ) : ImportStage {
        fun count(tab: ReportTab) = rows.count { it.tab == tab }

        /** Первая нерешённая строка: её показываем в блоке «Подтвердить». */
        val nextPending: Int get() = rows.indexOfFirst { it.pending }
    }
}

sealed interface ImportProblem {
    data class Unsupported(
        val extension: String,
        val supported: String,
    ) : ImportProblem

    data object Unreadable : ImportProblem

    data object Empty : ImportProblem
}

data class ImportState(
    val stage: ImportStage = ImportStage.PickFile,
    val problem: ImportProblem? = null,
    val supportedFormats: String = "",
) : UiState

sealed interface ImportIntent : UiIntent {
    class FilePicked(
        val fileName: String,
        val open: () -> InputStream?,
    ) : ImportIntent

    data class SelectTab(
        val tab: ReportTab,
    ) : ImportIntent

    data class Confirm(
        val row: Int,
        val track: Track,
    ) : ImportIntent

    data class Reject(
        val row: Int,
    ) : ImportIntent

    data class Restore(
        val row: Int,
    ) : ImportIntent

    /** Послушать найденный трек: играет весь найденный список с этого места. */
    data class Play(
        val row: Int,
    ) : ImportIntent

    data class Rename(
        val name: String,
    ) : ImportIntent

    data object Save : ImportIntent

    data object Restart : ImportIntent
}

sealed interface ImportEffect : UiEffect {
    data class Saved(
        val playlistId: PlaylistId,
        val name: String,
        val count: Int,
        /** Сколько не найденных треков ушло в «Хочу добавить». */
        val wished: Int = 0,
    ) : ImportEffect
}

@HiltViewModel
class ImportViewModel
    @Inject
    constructor(
        private val importPlaylist: ImportPlaylistUseCase,
        private val savePlaylist: SaveImportedPlaylistUseCase,
        private val player: PlayerController,
    ) : MviViewModel<ImportState, ImportIntent, ImportEffect>(
            ImportState(supportedFormats = importPlaylist.supportedExtensions.joinToString(", ") { it.uppercase() }),
        ) {
        override fun onIntent(intent: ImportIntent) {
            when (intent) {
                is ImportIntent.FilePicked -> read(intent)
                is ImportIntent.SelectTab -> updateReport { copy(tab = intent.tab) }
                is ImportIntent.Confirm -> updateRow(intent.row) { copy(chosen = intent.track, rejected = false) }
                is ImportIntent.Reject -> updateRow(intent.row) { copy(chosen = null, rejected = true) }
                is ImportIntent.Restore -> updateRow(intent.row) { copy(chosen = null, rejected = false) }
                is ImportIntent.Rename -> updateReport { copy(name = intent.name) }
                is ImportIntent.Play -> play(intent.row)
                ImportIntent.Save -> save()
                ImportIntent.Restart -> setState { copy(stage = ImportStage.PickFile, problem = null) }
            }
        }

        private fun play(row: Int) {
            val rows = (currentState.stage as? ImportStage.Report)?.rows ?: return
            val target = rows.getOrNull(row)?.track ?: return
            val tracks = rows.mapNotNull { it.track }
            player.playQueue(tracks, startIndex = tracks.indexOf(target).coerceAtLeast(0))
        }

        private fun read(intent: ImportIntent.FilePicked) {
            setState { copy(stage = ImportStage.Reading, problem = null) }
            viewModelScope.launch {
                val outcome =
                    try {
                        intent.open()?.use { importPlaylist(intent.fileName, it) }
                    } catch (_: IOException) {
                        null
                    }
                setState {
                    when (outcome) {
                        is ImportOutcome.Parsed -> {
                            val rows = outcome.items.map(::ReportRow).toImmutableList()
                            val tab = if (rows.any { it.pending }) ReportTab.Doubtful else ReportTab.Found
                            copy(stage = ImportStage.Report(outcome.name, outcome.format, rows, tab))
                        }

                        is ImportOutcome.Unsupported -> {
                            failed(
                                ImportProblem.Unsupported(
                                    outcome.extension,
                                    outcome.supported.joinToString(", ") { it.uppercase() },
                                ),
                            )
                        }

                        ImportOutcome.Empty -> {
                            failed(ImportProblem.Empty)
                        }

                        is ImportOutcome.Failed, null -> {
                            failed(ImportProblem.Unreadable)
                        }
                    }
                }
            }
        }

        private fun ImportState.failed(problem: ImportProblem) = copy(stage = ImportStage.PickFile, problem = problem)

        private fun save() {
            val report = currentState.stage as? ImportStage.Report ?: return
            val tracks = report.rows.mapNotNull { it.track }.distinctBy { it.id }
            val missing = missingOf(report.rows)
            val nothing = tracks.isEmpty() && missing.isEmpty()
            if (nothing || report.name.isBlank() || report.saving) return
            updateReport { copy(saving = true) }
            viewModelScope.launch {
                val id = savePlaylist(report.name, tracks, missing)
                sendEffect(ImportEffect.Saved(id, report.name, tracks.size, missing.size))
            }
        }

        /** Строки без трека в коллекции с местом, которое им достанется в плейлисте. */
        private fun missingOf(rows: List<ReportRow>): List<WishRequest> {
            val placed = HashSet<TrackId>()
            return rows.mapNotNull { row ->
                val track = row.track
                if (track != null) {
                    placed += track.id
                    return@mapNotNull null
                }
                val ref = row.item.ref
                WishRequest(
                    title = ref.title,
                    artist = ref.artist,
                    album = ref.album,
                    durationMs = ref.durationMs,
                    reason = WishReason.Import,
                    position = placed.size,
                )
            }
        }

        private fun updateReport(change: ImportStage.Report.() -> ImportStage.Report) =
            setState { (stage as? ImportStage.Report)?.let { copy(stage = it.change()) } ?: this }

        private fun updateRow(
            index: Int,
            change: ReportRow.() -> ReportRow,
        ) = updateReport {
            copy(rows = rows.mapIndexed { i, row -> if (i == index) row.change() else row }.toImmutableList())
        }
    }
