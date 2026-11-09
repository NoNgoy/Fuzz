package dev.fuzz.connect.script

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Описание скриптового плагина (формат версии 1). Код идёт отдельно. [domains] — единственные хосты
 * (и их поддомены), куда скрипт может ходить по HTTPS. [capabilities]: `source` (поиск и поток),
 * `lyrics` (тексты).
 */
@Serializable
data class ScriptHeader(
    val version: Int,
    val id: String,
    val name: String,
    val domains: List<String>,
    val capabilities: Set<String> = setOf(CAP_SOURCE),
) {
    companion object {
        const val CAP_SOURCE = "source"
        const val CAP_LYRICS = "lyrics"
    }
}

object ScriptHeaderParser {
    const val SUPPORTED_VERSION = 1
    private const val MAX_CODE_CHARS = 512 * 1024
    private val json = Json { ignoreUnknownKeys = false }
    private val idPattern = Regex("[a-z0-9][a-z0-9-]{1,31}")
    private val hostPattern = Regex("[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+")
    private val knownCapabilities = setOf(ScriptHeader.CAP_SOURCE, ScriptHeader.CAP_LYRICS)

    sealed interface Result {
        data class Valid(val header: ScriptHeader) : Result

        data class Invalid(val errors: List<String>) : Result
    }

    fun parse(headerJson: String, code: String): Result {
        val header =
            try {
                json.decodeFromString<ScriptHeader>(headerJson)
            } catch (e: SerializationException) {
                return Result.Invalid(listOf("Не удалось разобрать описание: ${e.message?.lineSequence()?.first()}"))
            } catch (e: IllegalArgumentException) {
                return Result.Invalid(listOf("Неверный формат: ${e.message?.lineSequence()?.first()}"))
            }
        val errors =
            buildList {
                if (header.version != SUPPORTED_VERSION) add("Версия ${header.version} не поддерживается, нужна 1")
                if (!idPattern.matches(header.id)) add("id должен быть из латиницы, цифр и дефиса, 2–32 символа")
                if (header.name.isBlank()) add("Название не может быть пустым")
                if (header.domains.isEmpty()) add("Нужен хотя бы один домен")
                header.domains.filterNot { hostPattern.matches(it) }.forEach { add("Неверный домен: $it") }
                (header.capabilities - knownCapabilities).forEach { add("Неизвестная возможность: $it") }
                if (header.capabilities.isEmpty()) add("Нужна хотя бы одна возможность")
                if (code.isBlank()) add("Код плагина пуст")
                if (code.length > MAX_CODE_CHARS) add("Код плагина больше 512 КБ")
            }
        return if (errors.isEmpty()) Result.Valid(header) else Result.Invalid(errors)
    }
}
