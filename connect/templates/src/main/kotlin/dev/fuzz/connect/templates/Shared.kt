package dev.fuzz.connect.templates

import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.network.safeCall

internal inline fun <T, R> PluginResult<T>.map(transform: (T) -> R): PluginResult<R> =
    when (this) {
        is PluginResult.Ok -> PluginResult.Ok(transform(value))
        is PluginResult.Failure -> this
    }

internal inline fun <T, R> PluginResult<T>.flatMap(transform: (T) -> PluginResult<R>): PluginResult<R> =
    when (this) {
        is PluginResult.Ok -> transform(value)
        is PluginResult.Failure -> this
    }

/** Сервер ответил «нет доступа»: сессию надо получить заново. */
internal class AuthException(message: String) : java.io.IOException(message)

private sealed interface Attempt<out T> {
    data class Done<T>(val value: T) : Attempt<T>

    data class Rejected(val cause: AuthException) : Attempt<Nothing>
}

/**
 * Вызов с сессией: если сервер отверг её ([AuthException]), сессия сбрасывается и берётся заново
 * один раз. Повторный отказ даёт UNAUTHORIZED.
 */
internal suspend fun <S : Any, T> withAuthRetry(
    session: suspend () -> PluginResult<S>,
    invalidate: suspend (S) -> Unit,
    block: suspend (S) -> T,
    retries: Int = 1,
): PluginResult<T> =
    session().flatMap { current ->
        safeCall {
            try {
                Attempt.Done(block(current))
            } catch (e: AuthException) {
                Attempt.Rejected(e)
            }
        }.flatMap { attempt ->
            when (attempt) {
                is Attempt.Done -> {
                    PluginResult.Ok(attempt.value)
                }

                is Attempt.Rejected -> {
                    invalidate(current)
                    if (retries > 0) {
                        withAuthRetry(session, invalidate, block, retries - 1)
                    } else {
                        PluginResult.Failure(PluginResult.Kind.UNAUTHORIZED, attempt.cause.message)
                    }
                }
            }
        }
    }
