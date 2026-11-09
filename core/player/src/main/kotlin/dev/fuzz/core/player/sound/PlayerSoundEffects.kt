package dev.fuzz.core.player.sound

import dev.fuzz.core.domain.player.EqualizerBands
import dev.fuzz.core.domain.player.SoundEffects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Сервис плеера сообщает сюда полосы эквалайзера, экран звука их читает. */
@Singleton
internal class PlayerSoundEffects
    @Inject
    constructor() : SoundEffects {
        private val mutable = MutableStateFlow<EqualizerBands?>(null)
        override val bands: StateFlow<EqualizerBands?> = mutable.asStateFlow()

        fun publish(bands: EqualizerBands?) {
            mutable.value = bands
        }
    }
