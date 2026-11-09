package dev.fuzz.feature.settings

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fuzz.core.domain.backup.BackupFormatException
import dev.fuzz.core.domain.backup.BackupRepository
import dev.fuzz.core.domain.backup.BackupSummary
import dev.fuzz.core.domain.backup.RestoreSummary
import dev.fuzz.core.domain.library.CollectionEditor
import dev.fuzz.core.domain.library.SyncLocalLibraryUseCase
import dev.fuzz.core.domain.library.SyncOutcome
import dev.fuzz.core.domain.settings.AccentPreference
import dev.fuzz.core.domain.settings.AppearanceRepository
import dev.fuzz.core.domain.settings.AppearanceSettings
import dev.fuzz.core.domain.settings.ThemePreference
import dev.fuzz.core.domain.stats.ListeningStatsRepository
import dev.fuzz.core.ui.mvi.MviViewModel
import dev.fuzz.core.ui.mvi.UiEffect
import dev.fuzz.core.ui.mvi.UiIntent
import dev.fuzz.core.ui.mvi.UiState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

data class SettingsState(
    val appearance: AppearanceSettings = AppearanceSettings(),
    val hiddenTracks: Int = 0,
    val isRescanning: Boolean = false,
    val isBackingUp: Boolean = false,
) : UiState

sealed interface SettingsIntent : UiIntent {
    data class SetTheme(
        val theme: ThemePreference,
    ) : SettingsIntent

    data class SetAccent(
        val accent: AccentPreference,
    ) : SettingsIntent

    data object Rescan : SettingsIntent

    data object RestoreHidden : SettingsIntent

    data object ClearHistory : SettingsIntent

    /** [uri] — файл, выбранный в системном окне «Сохранить». */
    data class ExportBackup(
        val uri: String,
    ) : SettingsIntent

    data class RestoreBackup(
        val uri: String,
    ) : SettingsIntent
}

sealed interface SettingsEffect : UiEffect {
    data class Rescanned(
        val outcome: SyncOutcome,
    ) : SettingsEffect

    data class Restored(
        val count: Int,
    ) : SettingsEffect

    data object HistoryCleared : SettingsEffect

    data class BackupSaved(
        val summary: BackupSummary,
    ) : SettingsEffect

    data class BackupRestored(
        val summary: RestoreSummary,
    ) : SettingsEffect

    /** [notBackup] — файл не копия Fuzz; иначе не удалось прочитать или записать файл. */
    data class BackupFailed(
        val notBackup: Boolean,
    ) : SettingsEffect
}

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val appearance: AppearanceRepository,
        private val collection: CollectionEditor,
        private val history: ListeningStatsRepository,
        private val sync: SyncLocalLibraryUseCase,
        private val backup: BackupRepository,
    ) : MviViewModel<SettingsState, SettingsIntent, SettingsEffect>(SettingsState()) {
        init {
            combine(appearance.settings, collection.observeHiddenCount()) { look, hidden ->
                setState { copy(appearance = look, hiddenTracks = hidden) }
            }.launchIn(viewModelScope)
        }

        override fun onIntent(intent: SettingsIntent) {
            when (intent) {
                is SettingsIntent.SetTheme -> {
                    viewModelScope.launch { appearance.setTheme(intent.theme) }
                }

                is SettingsIntent.SetAccent -> {
                    viewModelScope.launch { appearance.setAccent(intent.accent) }
                }

                SettingsIntent.Rescan -> {
                    if (currentState.isRescanning) return
                    setState { copy(isRescanning = true) }
                    viewModelScope.launch {
                        val outcome = sync()
                        setState { copy(isRescanning = false) }
                        sendEffect(SettingsEffect.Rescanned(outcome))
                    }
                }

                SettingsIntent.RestoreHidden -> {
                    val count = currentState.hiddenTracks
                    viewModelScope.launch {
                        collection.restoreAll()
                        sendEffect(SettingsEffect.Restored(count))
                    }
                }

                is SettingsIntent.ExportBackup -> {
                    runBackup { SettingsEffect.BackupSaved(backup.export(intent.uri)) }
                }

                is SettingsIntent.RestoreBackup -> {
                    runBackup { SettingsEffect.BackupRestored(backup.restore(intent.uri)) }
                }

                SettingsIntent.ClearHistory -> {
                    viewModelScope.launch {
                        history.clearHistory()
                        sendEffect(SettingsEffect.HistoryCleared)
                    }
                }
            }
        }

        @Suppress("SwallowedException") // причина уходит человеку сообщением, а не в лог
        private fun runBackup(action: suspend () -> SettingsEffect) {
            if (currentState.isBackingUp) return
            setState { copy(isBackingUp = true) }
            viewModelScope.launch {
                val effect =
                    try {
                        action()
                    } catch (e: BackupFormatException) {
                        SettingsEffect.BackupFailed(notBackup = true)
                    } catch (e: IOException) {
                        SettingsEffect.BackupFailed(notBackup = false)
                    }
                setState { copy(isBackingUp = false) }
                sendEffect(effect)
            }
        }
    }
