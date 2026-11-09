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

    /** Плагин на JavaScript: описание и код, работает в песочнице без доступа к телефону. */
    Script,
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
    /** Текст манифеста для [ConnectionKind.Manifest] или описание плагина для [ConnectionKind.Script]. */
    val manifest: String = "",
    /** Код плагина для [ConnectionKind.Script]. */
    val script: String = "",
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

    /**
     * Сдвигает подключение на [offset] мест в списке (−1 — выше). Порядок списка — порядок источников:
     * в поиске выше результаты верхних, волна и тексты спрашивают их первыми.
     */
    suspend fun move(
        id: String,
        offset: Int,
    )

    /** Проверка сохранённого подключения тем же пробным поиском, что и при сохранении. */
    suspend fun check(id: String): ConnectionCheck

    /** Последние запросы подключения, новые сверху. Пусто, если подключение журнал не ведёт. */
    fun journal(id: String): List<RequestLine>
}

/** Запись журнала запросов: без ключей и токенов, только путь, итог и время. */
data class RequestLine(
    val atMs: Long,
    val path: String,
    /** HTTP-код или null, если ответа не было. */
    val status: Int?,
    val durationMs: Long,
    val error: String? = null,
)

/** Что даёт одно подключение: всегда источник музыки, свой API может ещё тексты и похожих. */
class ConnectionPlugins(
    val source: MusicSource,
    val lyrics: LyricsProvider? = null,
    val metadata: MetadataProvider? = null,
    /** Журнал запросов, если подключение его ведёт (свой API). */
    val journal: () -> List<RequestLine> = { emptyList() },
    /** Освобождает ресурсы: песочницу JS-плагина. Вызывается, когда подключение пересобрано или удалено. */
    val close: () -> Unit = {},
)

/** Включённые подключения: источники видят поиск и волна, тексты — плеер. */
interface ConnectedSources {
    val sources: StateFlow<List<MusicSource>>

    val lyrics: StateFlow<List<LyricsProvider>>

    val metadata: StateFlow<List<MetadataProvider>>
}

/** Собирает плагины подключения из настроек. Живёт в app: только он знает все плагины. */
interface MusicSourceFactory {
    /**
     * Плагины или [IllegalArgumentException] с понятной причиной (битый манифест, неверный адрес).
     * [onSecretRotated] получает новый секрет, если сервер его сменил (refresh token в OAuth 2).
     */
    fun create(
        config: ConnectionConfig,
        secret: String?,
        onSecretRotated: (String) -> Unit = {},
    ): ConnectionPlugins
}
