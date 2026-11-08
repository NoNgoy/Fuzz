package dev.fuzz.connect.manifest

import dev.fuzz.core.domain.plugin.LyricsProvider
import dev.fuzz.core.domain.plugin.MetadataProvider
import dev.fuzz.core.domain.plugin.MusicSource
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
) {
    private val http = ManifestHttp(client, manifest, secret, limiter)

    val source: MusicSource = ManifestSource(manifest, http)
    val lyrics: LyricsProvider? = manifest.lyrics?.let { ManifestLyricsProvider(manifest, it, http) }
    val metadata: MetadataProvider? =
        manifest.similar?.takeIf { it.tracks != null || it.artists != null }
            ?.let { ManifestMetadataProvider(manifest, it, http) }

    companion object {
        const val MIN_INTERVAL_MS = 300L
    }
}
