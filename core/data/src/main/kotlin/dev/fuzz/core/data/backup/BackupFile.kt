package dev.fuzz.core.data.backup

import kotlinx.serialization.Serializable

/** Формат файла резервной копии. Поля только добавляются: старая копия читается новой версией. */
@Serializable
internal data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val createdAt: Long,
    val tracks: List<BackupTrack> = emptyList(),
    val reactions: List<BackupReaction> = emptyList(),
    val playlists: List<BackupPlaylist> = emptyList(),
    val listens: List<BackupListen> = emptyList(),
    /** Настройки: UserPreferences без папок и паролей, protobuf в base64. */
    val settings: String? = null,
    /** Подключения, чей пароль не попал в копию. */
    val passwordless: List<String> = emptyList(),
) {
    companion object {
        const val FORMAT = "fuzz-backup"
        const val VERSION = 1
    }
}

@Serializable
internal data class BackupTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0,
    val uri: String,
    val artwork: String? = null,
    val trackNumber: Int? = null,
    val year: Int? = null,
    val format: String? = null,
    val addedAt: Long = 0,
    val source: String,
    val hidden: Boolean = false,
    val saved: BackupSavedRef? = null,
)

@Serializable
internal data class BackupSavedRef(
    val sourceKey: String,
    val origin: String,
    val externalIds: String,
)

@Serializable
internal data class BackupReaction(
    val trackId: String,
    val reaction: Int,
    val updatedAt: Long,
)

@Serializable
internal data class BackupPlaylist(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val tracks: List<String>,
)

@Serializable
internal data class BackupListen(
    val trackId: String,
    val startedAt: Long,
    val playedMs: Long,
    val completed: Boolean,
)
