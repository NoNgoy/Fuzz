package dev.fuzz.core.player

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Единственный плеер, который видят MediaSession, Android Auto и часы.
 *
 * Сейчас это тонкая обёртка над одним [ExoPlayer] в режиме gapless. На этапе 3 сюда добавится второй
 * движок для кроссфейда (у Media3 нет встроенного), а наружу по-прежнему будет смотреть один плеер.
 */
@OptIn(UnstableApi::class)
class FuzzPlayer(
    val engine: ExoPlayer,
) : ForwardingPlayer(engine)
