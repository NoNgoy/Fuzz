package dev.fuzz.core.player.sound

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
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
 * Применяет настройки звука к плееру сервиса: эквалайзер, бас и выравнивание на его аудиосессии,
 * пропуск тишины и кроссфейд.
 *
 * Кроссфейд с наложением: за несколько секунд до конца трека его хвост подхватывает второй, скрытый
 * плеер [tail] и плавно уводит громкость в ноль, а основной плеер уже переходит на следующий трек
 * и плавно её набирает. Наружу (уведомление, Android Auto) смотрит только основной, поэтому очередь
 * и «что играет» переключаются как обычно. Хвост делит с основным аудиосессию, так что эквалайзер
 * действует и на него.
 */
@OptIn(UnstableApi::class)
internal class SoundController(
    private val player: ExoPlayer,
    private val published: PlayerSoundEffects,
    settings: Flow<SoundSettings>,
    private val scope: CoroutineScope,
    private val tail: () -> ExoPlayer,
) {
    private var tailPlayer: ExoPlayer? = null
    private var preparedFor: String? = null
    private var fadeStartedAt = -1L
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
        tailPlayer?.release()
        tailPlayer = null
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
        finishCrossfade()
        if (current.fadeSeconds <= 0) {
            fader = null
            player.volume = 1f
            tailPlayer?.release()
            tailPlayer = null
            return
        }
        fader =
            scope.launch {
                while (isActive) {
                    tick(current.fadeSeconds * MS_PER_SECOND)
                    delay(if (player.isPlaying || fadeStartedAt >= 0) PLAYING_TICK_MS else IDLE_TICK_MS)
                }
            }
    }

    private fun tick(fadeMs: Long) {
        if (fadeStartedAt >= 0) {
            val t = (SystemClock.elapsedRealtime() - fadeStartedAt).toFloat() / fadeMs
            if (t >= 1f || !player.isPlaying) {
                finishCrossfade()
            } else {
                val (out, into) = crossfadeVolumes(t)
                tailPlayer?.volume = out
                player.volume = into
            }
            return
        }
        val item = player.currentMediaItem
        val canOverlap =
            player.isPlaying && player.hasNextMediaItem() && player.repeatMode != Player.REPEAT_MODE_ONE
        if (item == null || !canOverlap) return
        val position = player.currentPosition
        val duration = player.duration
        if (preparedFor != item.mediaId && crossfadeDue(position, duration, fadeMs, PREPARE_LEAD_MS)) {
            prepareTail(item)
        }
        if (preparedFor == item.mediaId && crossfadeDue(position, duration, fadeMs)) startCrossfade(position)
    }

    /** Хвост готовится заранее: к моменту наложения он уже буферизован и встаёт на место мгновенно. */
    private fun prepareTail(item: MediaItem) {
        val tail = tailPlayer ?: tail().also { tailPlayer = it }
        tail.setAudioSessionId(player.audioSessionId)
        tail.setMediaItem(item, player.currentPosition + PREPARE_LEAD_MS)
        tail.playWhenReady = false
        tail.prepare()
        preparedFor = item.mediaId
    }

    private fun startCrossfade(position: Long) {
        val tail = tailPlayer ?: return
        tail.seekTo(position)
        tail.volume = 1f
        tail.play()
        player.volume = 0f
        player.seekToNextMediaItem()
        fadeStartedAt = SystemClock.elapsedRealtime()
    }

    private fun finishCrossfade() {
        if (fadeStartedAt < 0 && preparedFor == null) return
        tailPlayer?.run {
            stop()
            clearMediaItems()
        }
        player.volume = 1f
        preparedFor = null
        fadeStartedAt = -1
    }

    private companion object {
        const val MS_PER_SECOND = 1_000L
        const val PLAYING_TICK_MS = 50L
        const val IDLE_TICK_MS = 300L

        /** За сколько до наложения начинать буферизовать хвост. */
        const val PREPARE_LEAD_MS = 5_000L
    }
}
