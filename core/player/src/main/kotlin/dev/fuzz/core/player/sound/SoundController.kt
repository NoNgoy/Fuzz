package dev.fuzz.core.player.sound

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dev.fuzz.core.domain.player.SoundSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Применяет настройки звука к плееру сервиса: эквалайзер и бас на его аудиосессии, пропуск тишины
 * и плавные переходы. Плавность — затухание и нарастание громкости одного плеера; настоящий кроссфейд
 * с наложением треков потребует второго движка в [dev.fuzz.core.player.FuzzPlayer].
 */
@OptIn(UnstableApi::class)
internal class SoundController(
    private val player: ExoPlayer,
    private val published: PlayerSoundEffects,
    settings: Flow<SoundSettings>,
    private val scope: CoroutineScope,
) {
    private var effects: AudioEffects? = null
    private var current = SoundSettings()
    private var fader: Job? = null

    init {
        player.addListener(
            object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) = attach(audioSessionId)
            },
        )
        attach(player.audioSessionId)
        scope.launch { settings.collect(::apply) }
    }

    fun release() {
        fader?.cancel()
        effects?.release()
        effects = null
        published.publish(null)
    }

    private fun attach(sessionId: Int) {
        effects?.release()
        effects = if (sessionId == C.AUDIO_SESSION_ID_UNSET) null else AudioEffects(sessionId)
        effects?.apply(current)
        published.publish(effects?.bands)
    }

    private fun apply(settings: SoundSettings) {
        val fadeChanged = settings.fadeSeconds != current.fadeSeconds
        current = settings
        effects?.apply(settings)
        player.skipSilenceEnabled = settings.skipSilence
        if (fadeChanged || fader == null) restartFader()
    }

    private fun restartFader() {
        fader?.cancel()
        val fadeMs = current.fadeSeconds * MS_PER_SECOND
        if (fadeMs <= 0) {
            fader = null
            player.volume = 1f
            return
        }
        fader =
            scope.launch {
                while (isActive) {
                    player.volume = fadeVolume(player.currentPosition, player.duration, fadeMs)
                    delay(if (player.isPlaying) PLAYING_TICK_MS else IDLE_TICK_MS)
                }
            }
    }

    private companion object {
        const val MS_PER_SECOND = 1_000L
        const val PLAYING_TICK_MS = 50L
        const val IDLE_TICK_MS = 300L
    }
}
