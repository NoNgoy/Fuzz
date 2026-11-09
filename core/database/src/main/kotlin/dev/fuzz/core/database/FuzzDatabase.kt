package dev.fuzz.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import dev.fuzz.core.database.dao.ListenDao
import dev.fuzz.core.database.dao.PlaylistDao
import dev.fuzz.core.database.dao.ReactionDao
import dev.fuzz.core.database.dao.SimilarityCacheDao
import dev.fuzz.core.database.dao.TrackDao
import dev.fuzz.core.database.entity.ListenEntity
import dev.fuzz.core.database.entity.PlaylistEntity
import dev.fuzz.core.database.entity.PlaylistTrackEntity
import dev.fuzz.core.database.entity.ReactionEntity
import dev.fuzz.core.database.entity.SimilarityCacheEntity
import dev.fuzz.core.database.entity.TrackEntity

/**
 * Единственный источник правды для экранов. Схема экспортируется в core/database/schemas,
 * каждая миграция проверяется тестом.
 */
@Database(
    entities = [
        TrackEntity::class,
        ReactionEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        ListenEntity::class,
        SimilarityCacheEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5),
    ],
)
abstract class FuzzDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    abstract fun reactionDao(): ReactionDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun listenDao(): ListenDao

    abstract fun similarityCacheDao(): SimilarityCacheDao
}
