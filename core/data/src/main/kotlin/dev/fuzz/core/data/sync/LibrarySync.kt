package dev.fuzz.core.data.sync

import android.util.Log
import dev.fuzz.core.domain.library.LocalMusicScanner
import dev.fuzz.core.domain.library.SyncLocalLibraryUseCase
import dev.fuzz.core.domain.library.SyncOutcome
import dev.fuzz.core.domain.onboarding.OnboardingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Держит коллекцию в актуальном виде, пока жив процесс: сканирует при старте,
 * при смене папок и когда MediaStore сообщает об изменениях (с паузой, чтобы копирование
 * сотни файлов не запускало сотню сканирований).
 */
@Singleton
class LibrarySync
    @Inject
    internal constructor(
        private val sync: SyncLocalLibraryUseCase,
        private val scanner: LocalMusicScanner,
        private val onboarding: OnboardingRepository,
    ) {
        private val mutex = Mutex()
        private var started = false

        @OptIn(FlowPreview::class)
        fun start(scope: CoroutineScope) {
            if (started) return
            started = true
            val foldersChanged = onboarding.state.map { it.folders }.distinctUntilChanged().map { }
            val filesChanged = scanner.changes().debounce(CHANGE_DEBOUNCE_MS)
            merge(foldersChanged, filesChanged)
                .onEach { rescan() }
                .launchIn(scope)
        }

        /** Пересканировать сейчас, например после выдачи разрешения. */
        suspend fun rescan(): SyncOutcome =
            mutex.withLock {
                sync().also { Log.i(TAG, "Сканирование: $it") }
            }

        private companion object {
            const val TAG = "LibrarySync"
            const val CHANGE_DEBOUNCE_MS = 2_000L
        }
    }
