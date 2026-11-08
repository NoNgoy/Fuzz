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
)

@Serializable
data class AuthSpec(
    /** none, apiKey, bearer или basic. */
    val type: String = "none",
    /** Для apiKey: куда класть ключ, query или header. */
    val `in`: String = "query",
    /** Для apiKey: имя параметра или заголовка. */
    val name: String = "api_key",
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
