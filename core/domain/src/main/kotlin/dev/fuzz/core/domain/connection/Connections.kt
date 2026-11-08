package dev.fuzz.core.domain.connection

import dev.fuzz.core.domain.plugin.LyricsProvider
import dev.fuzz.core.domain.plugin.MetadataProvider
import dev.fuzz.core.domain.plugin.MusicSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Виды своих подключений (раздел 8.4 ТЗ). */
enum class ConnectionKind {
    /** Subsonic, Navidrome, Gonic, Airsonic. */
    Subsonic,
    Jellyfin,
    Ampache,
    WebDav,

    /** Свой REST API, описанный JSON-манифестом. */
    Manifest,
}

/**
 * Настройки подключения без секрета. Пароль, токен или ключ API хранится отдельно, зашифрованным
 * ключом из Android Keystore, и на экран не возвращается.
 */
data class ConnectionConfig(
    val id: String,
    val kind: ConnectionKind,
    val name: String,
    /** Адрес сервера; для манифеста берётся из него самого. */
    val baseUrl: String,
    val username: String = "",
    /** Текст манифеста для [ConnectionKind.Manifest]. */
    val manifest: String = "",
    val enabled: Boolean = true,
    /** Сохранён ли секрет: экран показывает «пароль сохранён», не раскрывая его. */
    val hasSecret: Boolean = false,
)

/** Чем закончилась проверка подключения. */
sealed interface ConnectionCheck {
    data object Ok : ConnectionCheck

    /** Неверный логин, пароль или ключ. */
    data object Unauthorized : ConnectionCheck

    /** Сервер не ответил: нет сети, неверный адрес или он выключен. */
    data object Unreachable : ConnectionCheck

    /** Неверные настройки: адрес, манифест. [reason] показывается пользователю как есть. */
    data class Invalid(
        val reason: String?,
    ) : ConnectionCheck
}

interface ConnectionsRepository {
    val connections: Flow<List<ConnectionConfig>>

    /**
     * Проверяет и сохраняет. [secret] = null оставляет прежний секрет (при правке без смены пароля).
     * Возвращает результат проверки; при ошибке ничего не сохраняется.
     */
    suspend fun save(
        config: ConnectionConfig,
        secret: String?,
    ): ConnectionCheck

    suspend fun setEnabled(
        id: String,
        enabled: Boolean,
    )

    suspend fun remove(id: String)
}

/** Что даёт одно подключение: всегда источник музыки, свой API может ещё тексты и похожих. */
data class ConnectionPlugins(
    val source: MusicSource,
    val lyrics: LyricsProvider? = null,
    val metadata: MetadataProvider? = null,
)

/** Включённые подключения: источники видят поиск и волна, тексты — плеер. */
interface ConnectedSources {
    val sources: StateFlow<List<MusicSource>>

    val lyrics: StateFlow<List<LyricsProvider>>

    val metadata: StateFlow<List<MetadataProvider>>
}

/** Собирает плагины подключения из настроек. Живёт в app: только он знает все плагины. */
interface MusicSourceFactory {
    /** Плагины или [IllegalArgumentException] с понятной причиной (битый манифест, неверный адрес). */
    fun create(
        config: ConnectionConfig,
        secret: String?,
    ): ConnectionPlugins
}
