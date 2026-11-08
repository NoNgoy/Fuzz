package dev.fuzz.core.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Очередь запросов с минимальным интервалом (MusicBrainz: 1 запрос/с). */
class RateLimiter(private val minIntervalMs: Long, private val now: () -> Long = System::currentTimeMillis) {
    private val mutex = Mutex()
    private var last = 0L
    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock {
        val wait = last + minIntervalMs - now()
        if (wait > 0) delay(wait)
        try { block() } finally { last = now() }
    }
}
