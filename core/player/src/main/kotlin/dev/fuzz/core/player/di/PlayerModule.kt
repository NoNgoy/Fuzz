package dev.fuzz.core.player.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.fuzz.core.domain.player.PlayerController
import dev.fuzz.core.domain.player.SoundEffects
import dev.fuzz.core.player.controller.MediaControllerPlayerController
import dev.fuzz.core.player.sound.PlayerSoundEffects

@Module
@InstallIn(SingletonComponent::class)
internal interface PlayerModule {
    @Binds
    fun bindPlayerController(impl: MediaControllerPlayerController): PlayerController

    @Binds
    fun bindSoundEffects(impl: PlayerSoundEffects): SoundEffects
}
