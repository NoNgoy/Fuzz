package dev.fuzz.core.player.sound

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import dev.fuzz.core.domain.player.EqualizerBands
import dev.fuzz.core.domain.player.SoundSettings
import dev.fuzz.core.domain.player.gainsFor
import kotlin.math.roundToInt

/**
 * Эквалайзер и усиление баса Android на аудиосессии плеера. Телефон может не дать эффекты
 * (нет поддержки, занято другим приложением): тогда звук идёт как есть, а экран узнаёт, что полос нет.
 */
internal class AudioEffects(
    private val sessionId: Int,
) {
    private val equalizer: Equalizer? = create { Equalizer(PRIORITY, sessionId) }
    private val bassBoost: BassBoost? = create { BassBoost(PRIORITY, sessionId) }?.takeIf { it.strengthSupported }

    val bands: EqualizerBands? =
        equalizer?.let { eq ->
            val range = eq.bandLevelRange
            EqualizerBands(
                centersHz = (0 until eq.numberOfBands).map { eq.getCenterFreq(it.toShort()) / MILLI },
                minDb = range[0] / MILLIBELS_PER_DB,
                maxDb = range[1] / MILLIBELS_PER_DB,
                hasBassBoost = bassBoost != null,
            )
        }

    fun apply(settings: SoundSettings) {
        val eq = equalizer ?: return
        val bands = bands ?: return
        guard {
            eq.enabled = settings.equalizerEnabled
            if (settings.equalizerEnabled) {
                settings.gainsFor(bands).forEachIndexed { band, db ->
                    eq.setBandLevel(band.toShort(), (db * MILLIBELS_PER_DB).roundToInt().toShort())
                }
            }
        }
        bassBoost?.let { boost ->
            guard {
                val on = settings.equalizerEnabled && settings.bassBoost > 0f
                boost.enabled = on
                if (on) boost.setStrength((settings.bassBoost * MAX_STRENGTH).roundToInt().toShort())
            }
        }
    }

    fun release() {
        guard { equalizer?.release() }
        guard { bassBoost?.release() }
    }

    // Конструкторы и сеттеры AudioEffect бросают голый RuntimeException, когда движок эффектов недоступен.
    @Suppress("TooGenericExceptionCaught")
    private fun <T> create(block: () -> T): T? =
        try {
            block()
        } catch (e: RuntimeException) {
            // IllegalArgumentException, UnsupportedOperationException: эффекта нет на этом телефоне.
            Log.w(TAG, "audio effect unavailable", e)
            null
        }

    @Suppress("TooGenericExceptionCaught")
    private fun guard(block: () -> Unit) {
        try {
            block()
        } catch (e: RuntimeException) {
            // IllegalStateException: эффект уже освобождён или отобран системой.
            Log.w(TAG, "audio effect failed", e)
        }
    }

    private companion object {
        const val TAG = "FuzzAudioEffects"
        const val PRIORITY = 0
        const val MILLI = 1_000
        const val MILLIBELS_PER_DB = 100f
        const val MAX_STRENGTH = 1_000
    }
}
