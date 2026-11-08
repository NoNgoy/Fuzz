package dev.fuzz.core.domain.library

import dev.fuzz.core.domain.onboarding.OnboardingRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

sealed interface SyncOutcome {
    data class Synced(
        val stats: SyncStats,
    ) : SyncOutcome

    data object NoPermission : SyncOutcome

    /** Папки не выбраны: сканировать нечего, коллекцию не трогаем. */
    data object NoFolders : SyncOutcome
}

class SyncLocalLibraryUseCase
    @Inject
    constructor(
        private val scanner: LocalMusicScanner,
        private val onboarding: OnboardingRepository,
        private val store: LocalLibraryStore,
    ) {
        suspend operator fun invoke(): SyncOutcome {
            val folders = onboarding.state.first().folders
            if (folders.isEmpty()) return SyncOutcome.NoFolders
            return when (val result = scanner.scan(folders)) {
                ScanResult.NoPermission -> SyncOutcome.NoPermission
                is ScanResult.Success -> SyncOutcome.Synced(store.replaceLocal(result.tracks))
            }
        }
    }
