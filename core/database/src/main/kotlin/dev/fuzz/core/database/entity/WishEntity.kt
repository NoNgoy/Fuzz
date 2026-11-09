package dev.fuzz.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Строка «Хочу добавить»: трека нет в коллекции, но он нужен (из импорта, из копии, лайкнутое превью).
 * Если трек ждёт место в плейлисте, [playlistId] и [position] говорят, куда его поставить, когда он появится.
 */
@Entity(tableName = "wishlist", indices = [Index("playlist_id")])
data class WishEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String? = null,
    @ColumnInfo(name = "duration_ms") val durationMs: Long? = null,
    /** import, backup или preview. */
    val reason: String,
    @ColumnInfo(name = "playlist_id") val playlistId: String? = null,
    val position: Int? = null,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)
