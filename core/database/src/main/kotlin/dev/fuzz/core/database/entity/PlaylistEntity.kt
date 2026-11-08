package dev.fuzz.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Плейлист. Удаление мягкое, как у треков: синхронизация должна увидеть, что его не стало. */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
)

/** Место трека в плейлисте. Позиции плотные, с нуля; при перестановке плейлист переписывается целиком. */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlist_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("track_id")],
)
data class PlaylistTrackEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: String,
    val position: Int,
    @ColumnInfo(name = "track_id") val trackId: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

/** Плейлист с числом видимых треков для списка. */
data class PlaylistWithCount(
    val id: String,
    val name: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "track_count") val trackCount: Int,
)
