package dev.fuzz.core.data.onboarding

import androidx.datastore.core.DataStore
import dev.fuzz.core.datastore.UserPreferences
import dev.fuzz.core.datastore.copy
import dev.fuzz.core.datastore.musicFolderProto
import dev.fuzz.core.domain.onboarding.MusicFolder
import dev.fuzz.core.domain.onboarding.OnboardingRepository
import dev.fuzz.core.domain.onboarding.OnboardingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class DataStoreOnboardingRepository
    @Inject
    constructor(
        private val dataStore: DataStore<UserPreferences>,
    ) : OnboardingRepository {
        override val state: Flow<OnboardingState> =
            dataStore.data.map { prefs ->
                OnboardingState(
                    completed = prefs.onboardingCompleted,
                    folders =
                        if (prefs.foldersChosen) {
                            prefs.musicFoldersList.map { MusicFolder(it.id, it.label) }
                        } else {
                            DEFAULT_FOLDERS
                        },
                )
            }

        override suspend fun setFolders(folders: List<MusicFolder>) {
            dataStore.updateData { prefs ->
                prefs.copy {
                    foldersChosen = true
                    musicFolders.clear()
                    musicFolders +=
                        folders.map {
                            musicFolderProto {
                                id = it.id
                                label = it.label
                            }
                        }
                }
            }
        }

        override suspend fun complete() {
            dataStore.updateData { it.copy { onboardingCompleted = true } }
        }

        companion object {
            /** По макету: «Музыка» и «Загрузки» выбраны сразу. */
            val DEFAULT_FOLDERS =
                listOf(
                    MusicFolder(id = "Music/", label = "Музыка"),
                    MusicFolder(id = "Download/", label = "Загрузки"),
                )
        }
    }
