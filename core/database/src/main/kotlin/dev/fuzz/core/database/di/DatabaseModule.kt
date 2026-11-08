package dev.fuzz.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.fuzz.core.database.FuzzDatabase
import dev.fuzz.core.database.dao.TrackDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): FuzzDatabase = Room.databaseBuilder(context, FuzzDatabase::class.java, "fuzz.db").build()

    @Provides
    fun provideTrackDao(db: FuzzDatabase): TrackDao = db.trackDao()
}
