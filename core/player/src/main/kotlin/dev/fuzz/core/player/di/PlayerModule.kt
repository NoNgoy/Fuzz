package dev.fuzz.core.player.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.fuzz.core.domain.player.PlayerController
import dev.fuzz.core.player.controller.MediaControllerPlayerController

@Module
@InstallIn(SingletonComponent::class)
internal interface PlayerModule {
    @Binds
    fun bindPlayerController(impl: MediaControllerPlayerController): PlayerController
}
