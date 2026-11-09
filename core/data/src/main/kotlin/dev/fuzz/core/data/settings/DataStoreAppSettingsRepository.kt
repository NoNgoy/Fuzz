package dev.fuzz.core.data.settings

import androidx.datastore.core.DataStore
import dev.fuzz.core.datastore.AppSettingsProto
import dev.fuzz.core.datastore.UserPreferences
import dev.fuzz.core.datastore.appSettingsProto
import dev.fuzz.core.datastore.copy
import dev.fuzz.core.domain.settings.AppSettings
import dev.fuzz.core.domain.settings.AppSettingsRepository
import dev.fuzz.core.domain.settings.ScanRules
import dev.fuzz.core.domain.settings.StreamQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class DataStoreAppSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<UserPreferences>,
    ) : AppSettingsRepository {
        override val settings: Flow<AppSettings> =
            dataStore.data.map { it.appSettings() }.distinctUntilChanged()

        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            dataStore.updateData { prefs -> prefs.copy { app = transform(prefs.appSettings()).toProto() } }
        }
    }

internal fun UserPreferences.appSettings(): AppSettings = if (hasApp()) app.toModel() else AppSettings()

private fun AppSettingsProto.toModel(): AppSettings {
    val defaults = AppSettings()
    return AppSettings(
        scan =
            ScanRules(
                skipVoiceNotes = if (hasSkipVoiceNotes()) skipVoiceNotes else defaults.scan.skipVoiceNotes,
                minDurationSec =
                    if (hasMinDurationSec()) {
                        minDurationSec.coerceAtLeast(
                            0,
                        )
                    } else {
                        defaults.scan.minDurationSec
                    },
            ),
        pauseOnDisconnect = if (hasPauseOnDisconnect()) pauseOnDisconnect else defaults.pauseOnDisconnect,
        cacheLimitMb = if (hasCacheLimitMb()) cacheLimitMb.coerceAtLeast(0) else defaults.cacheLimitMb,
        wifiQuality = StreamQuality.entries.firstOrNull { it.name == wifiQuality } ?: defaults.wifiQuality,
        mobileQuality = StreamQuality.entries.firstOrNull { it.name == mobileQuality } ?: defaults.mobileQuality,
        artworkGestures = if (hasArtworkGestures()) artworkGestures else defaults.artworkGestures,
    )
}

private fun AppSettings.toProto() =
    appSettingsProto {
        skipVoiceNotes = scan.skipVoiceNotes
        minDurationSec = scan.minDurationSec
        pauseOnDisconnect = this@toProto.pauseOnDisconnect
        cacheLimitMb = this@toProto.cacheLimitMb
        wifiQuality = this@toProto.wifiQuality.name
        mobileQuality = this@toProto.mobileQuality.name
        artworkGestures = this@toProto.artworkGestures
    }
