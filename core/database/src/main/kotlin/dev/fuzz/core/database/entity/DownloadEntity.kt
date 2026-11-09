package dev.fuzz.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** Трек из сети, скачанный на телефон в папку приложения: играет без интернета. */
@Entity(
    tableName = "downloads",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DownloadEntity(
    @PrimaryKey @ColumnInfo(name = "track_id") val trackId: String,
    val path: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "downloaded_at") val downloadedAt: Long,
)
