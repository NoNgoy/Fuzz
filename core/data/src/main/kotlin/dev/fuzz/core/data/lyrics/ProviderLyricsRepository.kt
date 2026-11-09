package dev.fuzz.core.data.lyrics

import dev.fuzz.core.domain.connection.ConnectedSources
import dev.fuzz.core.domain.lyrics.LyricsRepository
import dev.fuzz.core.domain.lyrics.LyricsResult
import dev.fuzz.core.domain.plugin.LyricsProvider
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.core.model.Track
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Тексты песен: сначала свои API из «Подключений», потом встроенные (LRCLIB). Синхронный текст
 * предпочтительнее обычного. Найденный текст сохраняется на диск и дальше открывается без сети;
 * в памяти держатся последние ответы, «нет текста» — только если ответили все источники.
 */
@Singleton
internal class ProviderLyricsRepository
    @Inject
    constructor(
        private val builtIn: List<@JvmSuppressWildcards LyricsProvider>,
        private val connected: ConnectedSources,
        private val store: LyricsStore,
    ) : LyricsRepository {
        private val mutex = Mutex()
        private val cache =
            object : LinkedHashMap<String, LyricsResult>(CACHE_SIZE, LOAD_FACTOR, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LyricsResult>) =
                    size > CACHE_SIZE
            }

        override suspend fun lyrics(track: Track): LyricsResult {
            val key = "${track.artistName.lowercase()}|${track.title.lowercase()}"
            mutex.withLock { cache[key] }?.let { return it }
            val result = store.get(key) ?: fetch(track).also { if (it is LyricsResult.Found) store.put(key, it) }
            if (result != LyricsResult.Unavailable) mutex.withLock { cache[key] = result }
            return result
        }

        private suspend fun fetch(track: Track): LyricsResult {
            val ref = TrackRef(track.title, track.artistName, track.albumTitle, track.durationMs.takeIf { it > 0 })
            var plain: LyricsResult.Found? = null
            var answered = false
            for (provider in connected.lyrics.value + builtIn) {
                when (val result = withTimeoutOrNull(TIMEOUT_MS) { provider.lyrics(ref) }) {
                    is PluginResult.Ok -> {
                        answered = true
                        val found = LyricsResult.Found(result.value, provider.displayName)
                        if (result.value.synced) return found
                        if (plain == null && result.value.lines.isNotEmpty()) plain = found
                    }

                    is PluginResult.Failure -> {
                        if (result.kind == PluginResult.Kind.NOT_FOUND) answered = true
                    }

                    // Таймаут: источник молчит, считаем недоступным.
                    null -> {}
                }
            }
            return plain ?: if (answered) LyricsResult.NotFound else LyricsResult.Unavailable
        }

        private companion object {
            const val CACHE_SIZE = 100
            const val LOAD_FACTOR = 0.75f
            const val TIMEOUT_MS = 8_000L
        }
    }
