package dev.fuzz.connect.script

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
internal data class HttpReply(val status: Int, val body: String = "", val error: String? = null)

/**
 * Единственный выход скрипта наружу: GET по HTTPS к доменам из описания и их поддоменам, с ограничением
 * размера ответа, частоты и числа запросов за вызов. Нарушение даёт ответ с `error`, а не исключение,
 * чтобы скрипт мог его обработать.
 */
internal class ScriptHttp(
    client: HttpClient,
    private val domains: List<String>,
    private val minIntervalMs: Long,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Без перехода по редиректам: иначе сервер мог бы увести запрос на чужой домен. */
    private val client = client.config { followRedirects = false }
    private val mutex = Mutex()
    private var last = 0L

    fun allowed(url: String): Boolean {
        val parsed = runCatching { Url(url) }.getOrNull() ?: return false
        if (!url.startsWith("https://") || parsed.port != HTTPS_PORT) return false
        val host = parsed.host.lowercase()
        return domains.any { host == it || host.endsWith(".$it") }
    }

    suspend fun get(url: String, headers: Map<String, String>): HttpReply {
        if (!allowed(url)) return HttpReply(0, error = "Адрес не разрешён описанием плагина: $url")
        val forbidden = headers.keys.firstOrNull { it.lowercase() in FORBIDDEN_HEADERS }
        if (forbidden != null) return HttpReply(0, error = "Заголовок $forbidden запрещён")
        return mutex.withLock {
            val wait = last + minIntervalMs - now()
            if (wait > 0) delay(wait)
            try {
                val response = client.get(url) { headers.forEach { (k, v) -> header(k, v) } }
                val text = response.bodyAsText()
                if (text.length > MAX_BODY_CHARS) {
                    HttpReply(response.status.value, error = "Ответ больше 2 МБ")
                } else {
                    HttpReply(response.status.value, text)
                }
            } catch (e: java.io.IOException) {
                HttpReply(0, error = e.message ?: "Сеть недоступна")
            } finally {
                last = now()
            }
        }
    }

    private companion object {
        const val HTTPS_PORT = 443
        const val MAX_BODY_CHARS = 2_000_000
        val FORBIDDEN_HEADERS = setOf("host", "cookie", "content-length", "transfer-encoding")
    }
}
