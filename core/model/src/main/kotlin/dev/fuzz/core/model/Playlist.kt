package dev.fuzz.core.model

import java.util.UUID

@JvmInline
value class PlaylistId(
    val value: String,
) {
    companion object {
        fun random(): PlaylistId = PlaylistId(UUID.randomUUID().toString())
    }
}

/** Плейлист пользователя. Треки читаются отдельно, чтобы список плейлистов не тянул тысячи строк. */
data class Playlist(
    val id: PlaylistId,
    val name: String,
    val trackCount: Int,
    val updatedAtMs: Long,
)
