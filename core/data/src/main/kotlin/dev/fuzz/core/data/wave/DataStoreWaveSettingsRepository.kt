package dev.fuzz.core.data.wave

import androidx.datastore.core.DataStore
import dev.fuzz.core.datastore.UserPreferences
import dev.fuzz.core.datastore.WaveSettingsProto
import dev.fuzz.core.datastore.copy
import dev.fuzz.core.datastore.waveSettingsProto
import dev.fuzz.core.domain.wave.WaveSettingsRepository
import dev.fuzz.core.model.wave.WaveActivity
import dev.fuzz.core.model.wave.WaveCharacter
import dev.fuzz.core.model.wave.WaveMood
import dev.fuzz.core.model.wave.WaveSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class DataStoreWaveSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<UserPreferences>,
    ) : WaveSettingsRepository {
        override val settings: Flow<WaveSettings> =
            dataStore.data
                .map { if (it.hasWave()) it.wave.toModel() else WaveSettings() }
                .distinctUntilChanged()

        override suspend fun update(transform: (WaveSettings) -> WaveSettings) {
            dataStore.updateData { prefs ->
                val current = if (prefs.hasWave()) prefs.wave.toModel() else WaveSettings()
                prefs.copy { wave = transform(current).toProto() }
            }
        }
    }

internal fun WaveSettingsProto.toModel() =
    WaveSettings(
        familiarity = familiarity.coerceIn(0, WaveSettings.MAX_FAMILIARITY),
        activity = WaveActivity.entries.firstOrNull { it.name == activity },
        mood = WaveMood.entries.firstOrNull { it.name == mood },
        character = WaveCharacter.entries.firstOrNull { it.name == character },
    )

internal fun WaveSettings.toProto() =
    waveSettingsProto {
        familiarity = this@toProto.familiarity
        activity = this@toProto.activity?.name.orEmpty()
        mood = this@toProto.mood?.name.orEmpty()
        character = this@toProto.character?.name.orEmpty()
    }
