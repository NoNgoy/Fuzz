package dev.fuzz.core.domain.player

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.ln

data class SoundSettings(
    val equalizerEnabled: Boolean = false,
    /** null — полосы настроены вручную ([customGainsDb]). */
    val preset: EqualizerPreset? = EqualizerPreset.Flat,
    /** Ручная настройка: по одному значению на полосу телефона, дБ. */
    val customGainsDb: List<Float> = emptyList(),
    /** Усиление баса 0..1. */
    val bassBoost: Float = 0f,
    /** Плавное затухание в конце трека и нарастание в начале; 0 — выключено. */
    val fadeSeconds: Int = 0,
    val skipSilence: Boolean = false,
    /** Выравнивание громкости: тихие треки подтягиваются, громкие не бьют по ушам. */
    val normalize: Boolean = false,
) {
    companion object {
        const val MAX_FADE_SECONDS = 12
    }
}

interface SoundSettingsRepository {
    val settings: Flow<SoundSettings>

    suspend fun update(transform: (SoundSettings) -> SoundSettings)
}

/** Полосы эквалайзера этого телефона; появляются, когда сервис плеера подключил эффекты к звуку. */
data class EqualizerBands(
    val centersHz: List<Int>,
    val minDb: Float,
    val maxDb: Float,
    val hasBassBoost: Boolean,
)

interface SoundEffects {
    /** null — эффекты недоступны: ничего не играло или телефон их не поддерживает. */
    val bands: StateFlow<EqualizerBands?>
}

/**
 * Пресет на полосах конкретного телефона: усиление между опорными частотами берётся по прямой
 * в логарифмической шкале частот, за краями — как у крайней опорной.
 */
fun EqualizerPreset.gainsFor(centersHz: List<Int>): List<Float> =
    centersHz.map { hz -> interpolate(hz, EqualizerPreset.REFERENCE_HZ, gainsDb) }

/** Что на самом деле подать на полосы: пресет или ручная настройка, обрезанные по возможностям телефона. */
fun SoundSettings.gainsFor(bands: EqualizerBands): List<Float> {
    val raw =
        preset?.gainsFor(bands.centersHz)
            ?: List(bands.centersHz.size) { customGainsDb.getOrElse(it) { 0f } }
    return raw.map { it.coerceIn(bands.minDb, bands.maxDb) }
}

private fun interpolate(
    hz: Int,
    xs: List<Int>,
    ys: List<Float>,
): Float {
    if (hz <= xs.first()) return ys.first()
    if (hz >= xs.last()) return ys.last()
    val right = xs.indexOfFirst { it >= hz }
    val left = right - 1
    val t = (ln(hz.toDouble()) - ln(xs[left].toDouble())) / (ln(xs[right].toDouble()) - ln(xs[left].toDouble()))
    return (ys[left] + (ys[right] - ys[left]) * t).toFloat()
}
