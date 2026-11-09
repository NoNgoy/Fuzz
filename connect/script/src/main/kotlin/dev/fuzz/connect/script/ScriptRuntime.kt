package dev.fuzz.connect.script

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.Closeable

/** Ошибка внутри скрипта: исключение JS, превышение времени или памяти. */
class ScriptException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Песочница одного плагина: свой экземпляр QuickJS без модулей, файлов и сети, кроме `fuzz.http.get`.
 * Код загружается один раз, вызовы идут по очереди. Обмен с Kotlin только строками JSON.
 */
internal class ScriptRuntime(
    private val code: String,
    private val http: ScriptHttp,
    private val dispatcher: CoroutineDispatcher,
    private val limits: ScriptLimits,
    private val log: (String) -> Unit,
) : Closeable {
    private val mutex = Mutex()
    private var js: QuickJs? = null

    /** Вычислить выражение JS (может вернуть Promise) и получить результат как строку JSON. */
    suspend fun callJson(expression: String): String? =
        mutex.withLock {
            val runtime = js ?: start().also { js = it }
            try {
                runtime.evaluate<String?>("JSON.stringify(await ($expression))", "call.js", false)
            } catch (e: QuickJsException) {
                throw ScriptException(e.message ?: "Ошибка скрипта", e)
            }
        }

    private suspend fun start(): QuickJs {
        val runtime =
            try {
                QuickJs.create(dispatcher)
            } catch (e: QuickJsException) {
                throw ScriptException("QuickJS не запустился: ${e.message}", e)
            }
        runtime.memoryLimit = limits.memoryBytes
        runtime.maxStackSize = limits.stackBytes
        runtime.evaluationTimeoutMillis = limits.timeoutMs
        runtime.asyncFunction("__fuzzHttpGet") { args ->
            val url = args.getOrNull(0) as? String ?: ""
            val headers =
                (args.getOrNull(1) as? String)?.let {
                    runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull()
                }.orEmpty()
            json.encodeToString(http.get(url, headers))
        }
        runtime.function("__fuzzLog") { args -> log(args.joinToString(" ").take(MAX_LOG_CHARS)) }
        try {
            runtime.evaluate<Any?>(PRELUDE, "prelude.js", false)
            runtime.evaluate<Any?>(code, "plugin.js", false)
        } catch (e: QuickJsException) {
            runtime.close()
            throw ScriptException("Код плагина не загрузился: ${e.message}", e)
        }
        return runtime
    }

    override fun close() {
        js?.close()
        js = null
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        const val MAX_LOG_CHARS = 500

        /** API для плагина. `plugin` объявляет сам скрипт. */
        val PRELUDE =
            """
            globalThis.fuzz = Object.freeze({
              http: Object.freeze({
                get: async (url, headers) => JSON.parse(await __fuzzHttpGet(String(url), JSON.stringify(headers || {}))),
              }),
              log: (...parts) => __fuzzLog(parts.map(String).join(" ")),
            });
            """.trimIndent()
    }
}

/** Ограничения песочницы. */
data class ScriptLimits(
    val memoryBytes: Long = DEFAULT_MEMORY_BYTES,
    val stackBytes: Long = MIB,
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    private companion object {
        const val MIB = 1_048_576L
        const val DEFAULT_MEMORY_BYTES = 33_554_432L
        const val DEFAULT_TIMEOUT_MS = 15_000L
    }
}
