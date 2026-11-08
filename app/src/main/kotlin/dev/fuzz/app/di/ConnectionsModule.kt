package dev.fuzz.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.fuzz.core.domain.connection.MusicSourceFactory

@Module
@InstallIn(SingletonComponent::class)
internal interface ConnectionsModule {
    @Binds
    fun bindMusicSourceFactory(impl: PluginSourceFactory): MusicSourceFactory
}
