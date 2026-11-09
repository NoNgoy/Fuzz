package dev.fuzz.core.data.search

import dev.fuzz.core.data.wave.ExternalTrackRegistry
import dev.fuzz.core.domain.connection.ConnectedSources
import dev.fuzz.core.domain.plugin.MusicSource
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.core.domain.search.CatalogSource
import dev.fuzz.core.domain.search.OnlineHit
import dev.fuzz.core.domain.search.OnlineOrigin
import dev.fuzz.core.domain.search.OnlineSearch
import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId
import dev.fuzz.engine.recommend.model.TrackOrigin
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject

/**
 * Поиск по своим серверам и встроенным каталогам. Найденное запоминается в [ExternalTrackRegistry]
 * так же, как треки волны, поэтому ссылку на поток плеер получает тем же путём.
 */
internal class PluginOnlineSearch
    @Inject
    constructor(
        private val connected: ConnectedSources,
        private val catalogs: List<@JvmSuppressWildcards CatalogSource>,
        private val registry: ExternalTrackRegistry,
    ) : OnlineSearch {
        override suspend fun search(
            query: String,
            origins: Set<OnlineOrigin>,
        ): List<OnlineHit> {
            if (query.isBlank()) return emptyList()
            val targets =
                buildList {
                    if (OnlineOrigin.Server in origins) {
                        connected.sources.value.forEach { add(Target(it, OnlineOrigin.Server, provider = null)) }
                    }
                    catalogs.filter { it.origin in origins }.forEach { add(Target(it.source, it.origin, it.provider)) }
                }
            val hits =
                coroutineScope {
                    targets.map { target -> async { target.search(query) } }.awaitAll().flatten()
                }
            return OnlineRanking.sort(query, hits) { it.track }
        }

        override suspend fun playable(tracks: List<Track>): List<Track> =
            coroutineScope {
                tracks
                    .map { track ->
                        async { if (track.playbackUri.isNotEmpty()) track else registry.resolve(track.id) }
                    }.awaitAll()
                    .filterNotNull()
            }

        private inner class Target(
            val source: MusicSource,
            val origin: OnlineOrigin,
            /** null: брать ключ из первого внешнего ID (у своих серверов он свой у каждого вида). */
            val provider: String?,
        ) {
            suspend fun search(query: String): List<OnlineHit> {
                val result = withTimeoutOrNull(TIMEOUT_MS) { source.search(query, LIMIT) }
                val refs = (result as? PluginResult.Ok)?.value?.tracks.orEmpty()
                return refs.mapNotNull { ref -> hit(ref) }
            }

            private fun hit(ref: TrackRef): OnlineHit? {
                val externalId =
                    (
                        provider?.let { key -> ref.externalIds.firstOrNull { it.provider == key } }
                            ?: ref.externalIds.firstOrNull()
                    )
                        ?: return null
                val id =
                    TrackId(UUID.nameUUIDFromBytes("${externalId.provider}:${externalId.id}".toByteArray()).toString())
                val trackOrigin = if (origin == OnlineOrigin.Preview) TrackOrigin.Preview else TrackOrigin.OpenCatalog
                registry.remember(id, source, ref, trackOrigin)
                val track =
                    Track(
                        id = id,
                        title = ref.title,
                        artistName = ref.artist,
                        albumTitle = ref.album,
                        durationMs = ref.durationMs ?: 0L,
                        playbackUri = "",
                        format = if (origin == OnlineOrigin.Preview) PREVIEW_LABEL else source.displayName,
                    )
                return OnlineHit(track, origin)
            }
        }

        private companion object {
            const val LIMIT = 50
            const val TIMEOUT_MS = 10_000L
            const val PREVIEW_LABEL = "Превью"
        }
    }
