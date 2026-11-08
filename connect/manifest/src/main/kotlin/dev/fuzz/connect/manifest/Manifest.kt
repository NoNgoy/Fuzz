package dev.fuzz.connect.manifest

import kotlinx.serialization.Serializable

/**
 * Декларативный манифест простого REST API (раздел 8.4 ТЗ, формат версии 1).
 * Это только данные: ни кода, ни скриптов. Секреты (ключ, токен) в манифест не входят, их передают отдельно.
 */
@Serializable
data class Manifest(
    val version: Int,
    val id: String,
    val name: String,
    val baseUrl: String,
    val auth: AuthSpec = AuthSpec(),
    val search: SearchSpec,
    val stream: StreamSpec,
    /** Необязательно: тексты песен. */
    val lyrics: LyricsSpec? = null,
    /** Необязательно: похожие треки и артисты для волны. */
    val similar: SimilarSpec? = null,
)

@Serializable
data class AuthSpec(
    /** none, apiKey, bearer, basic или oauth2. */
    val type: String = "none",
    /** Для apiKey: куда класть ключ, query или header. */
    val `in`: String = "query",
    /** Для apiKey: имя параметра или заголовка. */
    val name: String = "api_key",
    /** Для oauth2: адрес выдачи токена (https). */
    val tokenUrl: String? = null,
    /**
     * Для oauth2: `client_credentials` (секрет `client_id:client_secret`) или `refresh_token`
     * (секрет `client_id:client_secret:refresh_token`, client_secret может быть пустым).
     */
    val grant: String = "client_credentials",
    /** Для oauth2: необязательный scope. */
    val scope: String? = null,
)

@Serializable
data class SearchSpec(
    /** Путь относительно baseUrl, например `/search`. */
    val path: String,
    /** Параметры запроса; в значениях работают подстановки {query} и {limit}. */
    val query: Map<String, String> = emptyMap(),
    /** JSONPath-выражение, возвращающее массив найденных треков, например `$.data[*]`. */
    val results: String,
    val map: TrackMapping,
)

@Serializable
data class TrackMapping(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: String? = null,
    /** Если длительность в секундах, а не миллисекундах: множитель 1000. */
    val durationMultiplier: Long = 1,
)

@Serializable
data class StreamSpec(
    /** Шаблон ссылки на поток; подстановка {id} из внешнего ID трека. */
    val url: String,
)

/**
 * Запрос текста. В значениях [query] работают подстановки {title}, {artist}, {album}, {durationSec}, {id}.
 * Нужен хотя бы один из путей: [synced] (текст LRC с метками времени) или [plain] (обычный текст).
 */
@Serializable
data class LyricsSpec(
    val path: String,
    val query: Map<String, String> = emptyMap(),
    val synced: String? = null,
    val plain: String? = null,
)

@Serializable
data class SimilarSpec(
    /** Похожие треки: в [SimilarQuery.title] обязателен путь к названию. */
    val tracks: SimilarQuery? = null,
    val artists: SimilarQuery? = null,
)

/**
 * Запрос похожих. Подстановки {artist}, {title}, {id}, {limit}. [score] — путь к близости; если его нет,
 * близость убывает по порядку ответа. [scoreScale] делит её, например 100 для шкалы 0–100.
 */
@Serializable
data class SimilarQuery(
    val path: String,
    val query: Map<String, String> = emptyMap(),
    val results: String,
    val artist: String,
    val title: String? = null,
    val id: String? = null,
    val score: String? = null,
    val scoreScale: Double = 1.0,
)
