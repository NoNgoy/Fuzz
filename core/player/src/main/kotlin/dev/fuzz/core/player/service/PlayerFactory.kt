package dev.fuzz.core.player.service

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import dev.fuzz.core.player.FuzzPlayer

internal object PlayerFactory {
    fun create(context: Context): FuzzPlayer {
        val attributes =
            AudioAttributes
                .Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
        val exo =
            ExoPlayer
                .Builder(context)
                // Аудиофокус: пауза при звонке, приглушение при навигаторе.
                .setAudioAttributes(attributes, true)
                // Пауза при отключении наушников (настраиваемое поведение появится в настройках).
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
                .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
                .build()
        return FuzzPlayer(exo)
    }

    private const val SEEK_INCREMENT_MS = 10_000L
}
