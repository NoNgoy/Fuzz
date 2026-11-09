package dev.fuzz.core.domain.settings

import kotlinx.coroutines.flow.Flow

/** Что сканер не берёт в коллекцию. */
data class ScanRules(
    /** Диктофон, звонки, голосовые из мессенджеров. */
    val skipVoiceNotes: Boolean = true,
    /** Файлы короче — рингтоны, звуки уведомлений, обрывки. 0 — брать всё. */
    val minDurationSec: Int = DEFAULT_MIN_DURATION_SEC,
) {
    companion object {
        const val DEFAULT_MIN_DURATION_SEC = 30
        val DURATION_CHOICES = listOf(0, 15, 30, 60)
    }
}

/** Качество потока с серверов. [kbps] null — как есть на сервере, без перекодирования. */
@Suppress("MagicNumber") // Битрейты и есть смысл этих значений.
enum class StreamQuality(
    val kbps: Int?,
) {
    Original(null),
    High(320),
    Medium(192),
    Low(128),
}

/** Настройки из ТЗ (раздел «Всё, что можно настроить»), кроме звука, волны и оформления. */
data class AppSettings(
    val scan: ScanRules = ScanRules(),
    /** Пауза, когда отключились наушники или Bluetooth; иначе музыка продолжит из динамика. */
    val pauseOnDisconnect: Boolean = true,
    /** Кэш потоковых треков, МБ; 0 — без кэша. */
    val cacheLimitMb: Int = DEFAULT_CACHE_MB,
    val wifiQuality: StreamQuality = StreamQuality.Original,
    val mobileQuality: StreamQuality = StreamQuality.Medium,
    /** Свайп по обложке в плеере листает треки, двойное нажатие ставит лайк. */
    val artworkGestures: Boolean = true,
) {
    companion object {
        const val DEFAULT_CACHE_MB = 1_024
        val CACHE_CHOICES_MB = listOf(0, 256, 1_024, 4_096)
    }
}

interface AppSettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)
}
