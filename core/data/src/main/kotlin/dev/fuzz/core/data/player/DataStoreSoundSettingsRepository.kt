package dev.fuzz.core.data.player

import androidx.datastore.core.DataStore
import dev.fuzz.core.datastore.SoundProto
import dev.fuzz.core.datastore.UserPreferences
import dev.fuzz.core.datastore.copy
import dev.fuzz.core.datastore.soundProto
import dev.fuzz.core.domain.player.EqualizerPreset
import dev.fuzz.core.domain.player.SoundSettings
import dev.fuzz.core.domain.player.SoundSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class DataStoreSoundSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<UserPreferences>,
    ) : SoundSettingsRepository {
        override val settings: Flow<SoundSettings> =
            dataStore.data
                .map { if (it.hasSound()) it.sound.toModel() else SoundSettings() }
                .distinctUntilChanged()

        override suspend fun update(transform: (SoundSettings) -> SoundSettings) {
            dataStore.updateData { prefs ->
                val current = if (prefs.hasSound()) prefs.sound.toModel() else SoundSettings()
                prefs.copy { sound = transform(current).toProto() }
            }
        }
    }

private fun SoundProto.toModel() =
    SoundSettings(
        equalizerEnabled = equalizerEnabled,
        preset = EqualizerPreset.entries.firstOrNull { it.name == preset },
        customGainsDb = customGainsDbList,
        bassBoost = bassBoost.coerceIn(0f, 1f),
        fadeSeconds = fadeSeconds.coerceIn(0, SoundSettings.MAX_FADE_SECONDS),
        skipSilence = skipSilence,
    )

private fun SoundSettings.toProto() =
    soundProto {
        equalizerEnabled = this@toProto.equalizerEnabled
        preset = this@toProto.preset?.name.orEmpty()
        customGainsDb.addAll(this@toProto.customGainsDb)
        bassBoost = this@toProto.bassBoost
        fadeSeconds = this@toProto.fadeSeconds
        skipSilence = this@toProto.skipSilence
    }
