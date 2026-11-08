package dev.fuzz.core.network

import dev.fuzz.core.domain.plugin.PluginResult
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Каждый вызов подключения: таймаут + перевод исключений в PluginResult, чтобы сбой не ломал остальное. */
suspend fun <T> safeCall(timeoutMs: Long = 10_000, block: suspend () -> T): PluginResult<T> = try {
    PluginResult.Ok(withTimeout(timeoutMs) { block() })
} catch (e: TimeoutCancellationException) {
    PluginResult.Failure(PluginResult.Kind.TIMEOUT)
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    PluginResult.Failure(PluginResult.Kind.NETWORK, e.message)
}
