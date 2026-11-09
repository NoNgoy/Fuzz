package dev.fuzz.core.domain.library

import dev.fuzz.core.domain.onboarding.MusicFolder
import dev.fuzz.core.domain.settings.ScanRules
import kotlinx.coroutines.flow.Flow

/** Трек, найденный на устройстве. Ключ [id] стабилен между сканированиями. */
data class ScannedTrack(
    val id: String,
    val title: String,
    val artistName: String,
    val albumTitle: String?,
    val durationMs: Long,
    val playbackUri: String,
    val artworkUri: String?,
    val trackNumber: Int?,
    val year: Int?,
    val format: String?,
    val addedAtMs: Long,
)

sealed interface ScanResult {
    data class Success(
        val tracks: List<ScannedTrack>,
    ) : ScanResult

    /** Нет доступа к аудио: коллекцию трогать нельзя, иначе она опустеет. */
    data object NoPermission : ScanResult
}

/** Источник локальной музыки (source:local). */
interface LocalMusicScanner {
    suspend fun scan(
        folders: List<MusicFolder>,
        rules: ScanRules = ScanRules(),
    ): ScanResult

    /** Сигнал, что файлы на устройстве изменились и стоит пересканировать. */
    fun changes(): Flow<Unit>
}

data class SyncStats(
    val added: Int,
    val updated: Int,
    val removed: Int,
)

/** Запись результата сканирования в коллекцию: новое добавить, изменённое обновить, пропавшее скрыть. */
interface LocalLibraryStore {
    suspend fun replaceLocal(tracks: List<ScannedTrack>): SyncStats
}
