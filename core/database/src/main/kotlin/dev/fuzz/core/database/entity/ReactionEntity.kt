package dev.fuzz.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Лайк или дизлайк трека. Строка не удаляется, а получает reaction = 0, чтобы синхронизация видела снятие оценки. */
@Entity(tableName = "track_reactions")
data class ReactionEntity(
    @PrimaryKey @ColumnInfo(name = "track_id") val trackId: String,
    /** 0 — нет оценки, 1 — лайк, -1 — дизлайк. */
    val reaction: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
