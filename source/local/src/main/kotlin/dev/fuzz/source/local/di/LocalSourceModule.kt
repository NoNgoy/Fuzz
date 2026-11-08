package dev.fuzz.source.local.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.fuzz.core.domain.library.LocalMusicScanner
import dev.fuzz.source.local.MediaStoreScanner
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface LocalSourceModule {
    @Binds
    @Singleton
    fun bindScanner(impl: MediaStoreScanner): LocalMusicScanner
}
