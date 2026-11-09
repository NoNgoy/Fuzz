package dev.fuzz.core.player

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Единственный плеер, который видят MediaSession, Android Auto и часы.
 *
 * Тонкая обёртка над одним [ExoPlayer] в режиме gapless. Кроссфейд делает
 * [dev.fuzz.core.player.sound.SoundController] вторым, скрытым движком для хвоста трека,
 * а наружу по-прежнему смотрит один плеер.
 */
@OptIn(UnstableApi::class)
class FuzzPlayer(
    val engine: ExoPlayer,
) : ForwardingPlayer(engine)
