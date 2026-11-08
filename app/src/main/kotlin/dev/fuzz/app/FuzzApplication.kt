package dev.fuzz.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.fuzz.core.data.sync.LibrarySync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

@HiltAndroidApp
class FuzzApplication : Application() {
    @Inject lateinit var librarySync: LibrarySync

    /** Живёт столько же, сколько процесс: фоновая синхронизация коллекции. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        librarySync.start(appScope)
    }
}
