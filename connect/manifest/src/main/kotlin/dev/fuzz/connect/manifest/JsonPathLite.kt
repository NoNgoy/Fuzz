package dev.fuzz.connect.manifest

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Минимальный JSONPath без внешних зависимостей: `$`, `.поле`, `['поле']`, `[N]`, `[*]`.
 * Фильтров и рекурсивного спуска нет намеренно: манифест пишет посторонний человек, выражение должно быть
 * предсказуемым и не тратить время. Несуществующий путь даёт пустой результат, а не ошибку.
 */
object JsonPathLite {
    private val token = Regex("""\.([A-Za-z0-9_\-]+)|\['([^']*)']|\[(\d+)]|\[\*]""")

    fun isValid(path: String): Boolean = path.startsWith("$") && parse(path) != null

    fun select(root: JsonElement, path: String): List<JsonElement> {
        val steps = parse(path) ?: return emptyList()
        var current = listOf(root)
        for (step in steps) {
            current = current.flatMap { step(it) }
            if (current.isEmpty()) break
        }
        return current
    }

    private fun parse(path: String): List<Step>? {
        if (!path.startsWith("$")) return null
        val rest = path.substring(1)
        val steps = mutableListOf<Step>()
        var pos = 0
        while (pos < rest.length) {
            val match = token.matchAt(rest, pos) ?: return null
            steps += stepOf(match)
            pos = match.range.last + 1
        }
        return steps
    }

    private fun stepOf(match: MatchResult): Step {
        val field = match.groupValues[GROUP_DOT].ifEmpty { match.groupValues[GROUP_QUOTED] }
        val index = match.groupValues[GROUP_INDEX]
        return when {
            match.value == "[*]" -> Step { node -> children(node) }
            index.isNotEmpty() -> Step { node -> listOfNotNull((node as? JsonArray)?.getOrNull(index.toInt())) }
            else -> Step { node -> listOfNotNull((node as? JsonObject)?.get(field)) }
        }
    }

    private fun interface Step {
        operator fun invoke(node: JsonElement): List<JsonElement>
    }

    private const val GROUP_DOT = 1
    private const val GROUP_QUOTED = 2
    private const val GROUP_INDEX = 3

    private fun children(node: JsonElement): List<JsonElement> =
        when (node) {
            is JsonArray -> node.toList()
            is JsonObject -> node.values.toList()
            else -> emptyList()
        }
}
