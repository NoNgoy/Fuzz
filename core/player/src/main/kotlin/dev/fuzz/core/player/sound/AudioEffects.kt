package dev.fuzz.core.player.sound

import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
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
    private val leveler: DynamicsProcessing? = create { leveler(sessionId) }

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
        leveler?.let { guard { it.enabled = settings.normalize } }
        bassBoost?.let { boost ->
            guard {
                val on = settings.equalizerEnabled && settings.bassBoost > 0f
                boost.enabled = on
                if (on) boost.setStrength((settings.bassBoost * MAX_STRENGTH).roundToInt().toShort())
            }
        }
    }

    fun release() {
        guard { leveler?.release() }
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

    /**
     * Выравнивание громкости: компрессор на всю полосу прижимает громкое и поднимает тихое,
     * лимитер сверху не даёт перегрузить. Работает на звуке, поэтому не зависит от тегов ReplayGain.
     */
    private fun leveler(sessionId: Int): DynamicsProcessing {
        val config =
            DynamicsProcessing.Config
                .Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    CHANNELS,
                    false,
                    0,
                    true,
                    1,
                    false,
                    0,
                    true,
                ).build()
        return DynamicsProcessing(PRIORITY, sessionId, config).apply {
            setMbcBandAllChannelsTo(
                0,
                DynamicsProcessing.MbcBand(
                    true,
                    FULL_BAND_HZ,
                    COMP_ATTACK_MS,
                    COMP_RELEASE_MS,
                    COMP_RATIO,
                    COMP_THRESHOLD_DB,
                    COMP_KNEE_DB,
                    GATE_DB,
                    1f,
                    0f,
                    MAKEUP_GAIN_DB,
                ),
            )
            setLimiterAllChannelsTo(
                DynamicsProcessing.Limiter(
                    true,
                    true,
                    0,
                    LIMIT_ATTACK_MS,
                    LIMIT_RELEASE_MS,
                    LIMIT_RATIO,
                    LIMIT_THRESHOLD_DB,
                    0f,
                ),
            )
            enabled = false
        }
    }

    private companion object {
        const val CHANNELS = 2
        const val FULL_BAND_HZ = 20_000f
        const val COMP_ATTACK_MS = 10f
        const val COMP_RELEASE_MS = 300f
        const val COMP_RATIO = 3f
        const val COMP_THRESHOLD_DB = -28f
        const val COMP_KNEE_DB = 6f
        const val GATE_DB = -90f
        const val MAKEUP_GAIN_DB = 8f
        const val LIMIT_ATTACK_MS = 1f
        const val LIMIT_RELEASE_MS = 60f
        const val LIMIT_RATIO = 10f
        const val LIMIT_THRESHOLD_DB = -1f
        const val TAG = "FuzzAudioEffects"
        const val PRIORITY = 0
        const val MILLI = 1_000
        const val MILLIBELS_PER_DB = 100f
        const val MAX_STRENGTH = 1_000
    }
}
