package dev.fuzz.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.fuzz.core.database.dao.ReactionDao
import dev.fuzz.core.database.dao.TrackDao
import dev.fuzz.core.database.entity.ReactionEntity
import dev.fuzz.core.database.entity.TrackEntity

/**
 * Единственный источник правды для экранов. Схема экспортируется в core/database/schemas,
 * каждая миграция проверяется тестом.
 */
@Database(entities = [TrackEntity::class, ReactionEntity::class], version = 1, exportSchema = true)
abstract class FuzzDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    abstract fun reactionDao(): ReactionDao
}
