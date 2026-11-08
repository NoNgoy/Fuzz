package dev.fuzz.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Трек в локальной базе. Поля для синхронизации (updated_at, deleted) заложены с первого дня,
 * как требует документ архитектуры: удаление мягкое, время изменения ставится при каждой записи.
 */
@Entity(
    tableName = "tracks",
    indices = [Index("playback_uri", unique = true), Index("artist_name"), Index("album_title")],
)
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "artist_name") val artistName: String,
    @ColumnInfo(name = "album_title") val albumTitle: String?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "playback_uri") val playbackUri: String,
    @ColumnInfo(name = "artwork_uri") val artworkUri: String?,
    @ColumnInfo(name = "track_number") val trackNumber: Int?,
    val year: Int?,
    /** FLAC, MP3, Jamendo… для метки рядом с исполнителем. */
    val format: String?,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    /** Идентификатор плагина-источника: local, jamendo, subsonic… */
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
)
