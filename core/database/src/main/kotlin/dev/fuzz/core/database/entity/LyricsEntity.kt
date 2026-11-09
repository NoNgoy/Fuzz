package dev.fuzz.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Найденные тексты песен, чтобы они открывались без сети. Ключ — «артист|название» в нижнем регистре,
 * а не id трека: один и тот же трек бывает и файлом, и с сервера. Строки хранятся как «время\tтекст»,
 * время -1 у несинхронного текста.
 */
@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val key: String,
    val source: String,
    val synced: Boolean,
    val lines: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)
