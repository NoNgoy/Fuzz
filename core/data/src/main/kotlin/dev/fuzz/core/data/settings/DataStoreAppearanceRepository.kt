package dev.fuzz.core.data.settings

import androidx.datastore.core.DataStore
import dev.fuzz.core.datastore.UserPreferences
import dev.fuzz.core.datastore.copy
import dev.fuzz.core.domain.settings.AccentPreference
import dev.fuzz.core.domain.settings.AppearanceRepository
import dev.fuzz.core.domain.settings.AppearanceSettings
import dev.fuzz.core.domain.settings.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class DataStoreAppearanceRepository
    @Inject
    constructor(
        private val dataStore: DataStore<UserPreferences>,
    ) : AppearanceRepository {
        override val settings: Flow<AppearanceSettings> =
            dataStore.data
                .map { prefs ->
                    AppearanceSettings(
                        theme =
                            ThemePreference.entries.firstOrNull {
                                it.name == prefs.theme
                            } ?: ThemePreference.System,
                        accent =
                            AccentPreference.entries.firstOrNull { it.name == prefs.accent } ?: AccentPreference.Brand,
                    )
                }.distinctUntilChanged()

        override suspend fun setTheme(theme: ThemePreference) {
            dataStore.updateData { it.copy { this.theme = theme.name } }
        }

        override suspend fun setAccent(accent: AccentPreference) {
            dataStore.updateData { it.copy { this.accent = accent.name } }
        }
    }
