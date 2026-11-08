package dev.fuzz.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.fuzz.core.data.library.OfflineLibraryRepository
import dev.fuzz.core.domain.library.LibraryRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {
    @Binds
    @Singleton
    fun bindLibraryRepository(impl: OfflineLibraryRepository): LibraryRepository
}
