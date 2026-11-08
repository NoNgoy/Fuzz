package dev.fuzz.core.domain.player

/**
 * Заголовки для потока, которому нужна авторизация (например, Basic у WebDAV). Плеер спрашивает их
 * по ссылке перед каждым запросом, чтобы пароль не попадал в URL и логи.
 */
interface PlaybackHeaders {
    fun forUri(uri: String): Map<String, String>
}
