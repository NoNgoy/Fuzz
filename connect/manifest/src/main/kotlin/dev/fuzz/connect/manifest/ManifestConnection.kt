package dev.fuzz.connect.manifest

import dev.fuzz.core.domain.plugin.LyricsProvider
import dev.fuzz.core.domain.plugin.MetadataProvider
import dev.fuzz.core.domain.plugin.MusicSource
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.network.RateLimiter
import io.ktor.client.HttpClient

/**
 * Всё, что описывает один манифест: источник, а если в манифесте есть разделы `lyrics` и `similar`,
 * ещё тексты и похожие. Части делят одну очередь запросов, чтобы вместе не перегружать чужой API.
 */
class ManifestConnection(
    client: HttpClient,
    manifest: Manifest,
    secret: String? = null,
    limiter: RateLimiter = RateLimiter(MIN_INTERVAL_MS),
    /** Для oauth2 с refresh token: сервер выдал новый, его надо сохранить в Keystore вместо старого. */
    onRefreshTokenRotated: (String) -> Unit = {},
) {
    private val http = ManifestHttp(client, manifest, secret, limiter, onRefreshTokenRotated)

    val source: MusicSource = ManifestSource(manifest, http)
    val lyrics: LyricsProvider? = manifest.lyrics?.let { ManifestLyricsProvider(manifest, it, http) }
    val metadata: MetadataProvider? =
        manifest.similar?.takeIf { it.tracks != null || it.artists != null }
            ?.let { ManifestMetadataProvider(manifest, it, http) }

    /** Последние запросы (новые сверху), без ключей и токенов. */
    fun journal(): List<RequestLogEntry> = http.journal.snapshot()

    /** Кнопка «Проверить»: пробный поиск. Ok — сколько треков нашлось. */
    suspend fun check(query: String = CHECK_QUERY): PluginResult<Int> =
        when (val result = source.search(query, 1)) {
            is PluginResult.Ok -> PluginResult.Ok(result.value.tracks.size)
            is PluginResult.Failure -> result
        }

    companion object {
        const val CHECK_QUERY = "love"
        const val MIN_INTERVAL_MS = 300L
    }
}
