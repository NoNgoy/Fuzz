package dev.fuzz.core.domain.onboarding

import kotlinx.coroutines.flow.Flow

/**
 * Папка с музыкой, которую сканирует локальный источник.
 * [id] — относительный путь MediaStore («Music/») или URI дерева SAF для папки вне общих.
 */
data class MusicFolder(
    val id: String,
    val label: String,
)

data class OnboardingState(
    val completed: Boolean,
    val folders: List<MusicFolder>,
)

/** Первый запуск: выбранные папки и признак, что онбординг пройден. */
interface OnboardingRepository {
    val state: Flow<OnboardingState>

    suspend fun setFolders(folders: List<MusicFolder>)

    suspend fun complete()
}
