package dev.fuzz.core.domain.backup

/**
 * Резервная копия в файл (раздел 3.5 ТЗ): лайки, плейлисты, история, сохранённые треки из сети и настройки.
 * [uri] — документ, выбранный человеком в системном окне (content://…).
 */
interface BackupRepository {
    suspend fun export(uri: String): BackupSummary

    /** Добавляет из копии то, чего нет на телефоне; ничего не удаляет. */
    suspend fun restore(uri: String): RestoreSummary
}

data class BackupSummary(
    val likes: Int,
    val playlists: Int,
    val listens: Int,
)

data class RestoreSummary(
    val likes: Int,
    val playlists: Int,
    val listens: Int,
    /** Файлы с прежнего телефона, которых нет на этом: их можно докачать. */
    val missingFiles: Int,
    /** Подключения с паролем: пароль в копию не попадает, его нужно ввести заново. */
    val connectionsNeedingPassword: Int,
)

class BackupFormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
