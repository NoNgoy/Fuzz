package dev.fuzz.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.fuzz.core.data.sync.LibrarySync
import dev.fuzz.core.data.wave.ListenRecorder
import dev.fuzz.core.data.wave.WaveRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

@HiltAndroidApp
class FuzzApplication : Application() {
    @Inject lateinit var librarySync: LibrarySync

    @Inject lateinit var listenRecorder: ListenRecorder

    @Inject lateinit var waveRunner: WaveRunner

    /** Живёт столько же, сколько процесс: фоновая синхронизация коллекции, история прослушиваний и волна. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        librarySync.start(appScope)
        listenRecorder.start(appScope)
        waveRunner.attach(appScope)
    }
}
