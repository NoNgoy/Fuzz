package dev.fuzz.connect.manifest

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Разбор и проверка манифеста. Всё, что не прошло проверку, отвергается целиком: частично рабочих подключений нет. */
object ManifestParser {
    const val SUPPORTED_VERSION = 1
    private val json = Json { ignoreUnknownKeys = false }
    private val idPattern = Regex("[a-z0-9][a-z0-9-]{1,31}")
    private val authTypes = setOf("none", "apiKey", "bearer", "basic")

    sealed interface Result {
        data class Valid(val manifest: Manifest) : Result

        data class Invalid(val errors: List<String>) : Result
    }

    fun parse(text: String): Result {
        val manifest =
            try {
                json.decodeFromString<Manifest>(text)
            } catch (e: SerializationException) {
                return Result.Invalid(listOf("Не удалось разобрать JSON: ${e.message?.lineSequence()?.first()}"))
            } catch (e: IllegalArgumentException) {
                return Result.Invalid(listOf("Неверный формат: ${e.message?.lineSequence()?.first()}"))
            }
        val errors = validate(manifest)
        return if (errors.isEmpty()) Result.Valid(manifest) else Result.Invalid(errors)
    }

    internal fun validate(m: Manifest): List<String> =
        buildList {
            if (m.version !=
                SUPPORTED_VERSION
            ) {
                add("Версия формата ${m.version} не поддерживается, нужна $SUPPORTED_VERSION")
            }
            if (!idPattern.matches(m.id)) add("id должен быть из латиницы, цифр и дефиса, 2–32 символа")
            if (m.name.isBlank()) add("Название не может быть пустым")
            if (!m.baseUrl.startsWith("https://")) add("baseUrl должен начинаться с https://")
            if (m.auth.type !in authTypes) add("Неизвестный тип авторизации: ${m.auth.type}")
            if (m.auth.type == "apiKey" &&
                m.auth.`in` !in setOf("query", "header")
            ) {
                add("auth.in должен быть query или header")
            }
            if (!m.search.path.startsWith("/")) add("search.path должен начинаться с /")
            val paths =
                listOfNotNull(
                    m.search.results,
                    m.search.map.id,
                    m.search.map.title,
                    m.search.map.artist,
                    m.search.map.album,
                    m.search.map.durationMs,
                )
            paths.filterNot(JsonPathLite::isValid).forEach { add("Неверное JSONPath-выражение: $it") }
            if (!m.stream.url.startsWith("https://") || "{id}" !in m.stream.url) {
                add("stream.url должен начинаться с https:// и содержать {id}")
            }
            if (hostOf(m.stream.url) != hostOf(m.baseUrl)) add("stream.url должен вести на тот же домен, что и baseUrl")
        }

    internal fun hostOf(url: String): String =
        url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':').lowercase()
}
