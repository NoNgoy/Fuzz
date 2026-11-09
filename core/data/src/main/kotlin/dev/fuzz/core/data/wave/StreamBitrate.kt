package dev.fuzz.core.data.wave

import android.content.Context
import android.net.ConnectivityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.fuzz.core.domain.settings.AppSettings
import dev.fuzz.core.domain.settings.AppSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Предел битрейта для ссылки на поток прямо сейчас; null — без ограничения. */
fun interface StreamBitrate {
    fun maxKbps(): Int?
}

/**
 * Качество из настроек по типу сети: лимитная сеть (мобильная, точка доступа) — «мобильное»,
 * остальное — «Wi-Fi». Касается и потока, и скачивания: оба берут ссылку здесь.
 */
@Singleton
internal class NetworkStreamBitrate
    @Inject
    constructor(
        @ApplicationContext context: Context,
        settings: AppSettingsRepository,
    ) : StreamBitrate {
        private val connectivity = context.getSystemService(ConnectivityManager::class.java)
        private val current =
            settings.settings.stateIn(
                CoroutineScope(SupervisorJob() + Dispatchers.Default),
                SharingStarted.Eagerly,
                AppSettings(),
            )

        override fun maxKbps(): Int? {
            val app = current.value
            val metered = connectivity?.isActiveNetworkMetered ?: false
            return (if (metered) app.mobileQuality else app.wifiQuality).kbps
        }
    }
