package dev.fuzz.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Постоянный кэш ответов «похожие артисты/треки» для волны: переживает перезапуск и выручает без сети.
 * [kind] — `artist` или `track`, [key] — нормализованный запрос, [payload] — ответ в формате core:data.
 */
@Entity(tableName = "similarity_cache", primaryKeys = ["kind", "key"], indices = [Index("stored_at")])
data class SimilarityCacheEntity(
    val kind: String,
    val key: String,
    val payload: String,
    @ColumnInfo(name = "stored_at") val storedAt: Long,
)
