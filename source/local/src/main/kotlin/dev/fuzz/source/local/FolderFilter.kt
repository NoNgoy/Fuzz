package dev.fuzz.source.local

import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri
import dev.fuzz.core.domain.onboarding.MusicFolder

/**
 * Условие выборки MediaStore по папкам пользователя.
 * Относительный путь («Music/») ищется на всех томах, папка из системного выбора (URI дерева SAF)
 * переводится в том и путь MediaStore: «primary:Music/Rock» → external_primary + «Music/Rock/».
 */
internal data class FolderFilter(
    val selection: String,
    val args: List<String>,
) {
    companion object {
        fun of(folders: List<MusicFolder>): FolderFilter? {
            val clauses = mutableListOf<String>()
            val args = mutableListOf<String>()
            folders.mapNotNull { it.toLocation() }.distinct().forEach { location ->
                val pathClause = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? ESCAPE '\\'"
                if (location.volume == null) {
                    clauses += "($pathClause)"
                } else {
                    clauses += "(${MediaStore.Audio.Media.VOLUME_NAME} = ? AND $pathClause)"
                    args += location.volume
                }
                args += location.relativePath.escapeLike() + "%"
            }
            if (clauses.isEmpty()) return null
            return FolderFilter(clauses.joinToString(" OR ", prefix = "(", postfix = ")"), args)
        }
    }
}

internal data class FolderLocation(
    /** Том MediaStore или null, если папку надо искать на всех томах. */
    val volume: String?,
    /** Путь с «/» в конце, пустой для корня тома. */
    val relativePath: String,
)

internal fun MusicFolder.toLocation(): FolderLocation? =
    if (id.startsWith("content://")) {
        treeLocation(id.toUri())
    } else {
        FolderLocation(volume = null, relativePath = id.normalizePath())
    }

private fun treeLocation(uri: Uri): FolderLocation? {
    val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
    val volumeId = documentId.substringBefore(':', missingDelimiterValue = "")
    if (volumeId.isEmpty()) return null
    val volume = if (volumeId == "primary") MediaStore.VOLUME_EXTERNAL_PRIMARY else volumeId.lowercase()
    return FolderLocation(volume, documentId.substringAfter(':').normalizePath())
}

private fun String.normalizePath(): String {
    val trimmed = trim('/')
    return if (trimmed.isEmpty()) "" else "$trimmed/"
}

private fun String.escapeLike(): String = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
