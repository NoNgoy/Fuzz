package dev.fuzz.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * История прослушиваний для профиля вкуса волны. Без внешнего ключа на tracks: в историю попадают и треки
 * вне коллекции (превью, Jamendo), у которых нет строки в tracks.
 */
@Entity(tableName = "listen_events", indices = [Index("started_at"), Index("track_id")])
data class ListenEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "track_id") val trackId: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "played_ms") val playedMs: Long,
    val completed: Boolean,
)
