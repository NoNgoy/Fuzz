package dev.fuzz.core.data.wave

import dev.fuzz.core.domain.connection.ConnectedSources
import dev.fuzz.core.domain.plugin.MusicSource
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.SearchResult
import dev.fuzz.engine.recommend.model.TrackHint
import dev.fuzz.engine.recommend.model.TrackOrigin
import dev.fuzz.engine.recommend.model.WaveTrack
import dev.fuzz.engine.recommend.port.ExternalCatalog
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Свои серверы пользователя (Navidrome, Jellyfin, WebDAV, свой API) как каталог для волны: треки,
 * которых нет в коллекции на телефоне, но есть на сервере. Набор серверов берётся на каждый запрос,
 * так что включённое в «Подключениях» подхватывается без перезапуска.
 */
class ConnectedCatalog(
    private val connected: ConnectedSources,
    private val registry: ExternalTrackRegistry,
) : ExternalCatalog {
    override val origin: TrackOrigin = TrackOrigin.OpenCatalog

    override val searchesByTags: Boolean = false

    override suspend fun byArtist(
        artist: String,
        limit: Int,
    ): List<WaveTrack> {
        val key = WaveTrack.keyOf(artist)
        return searchAll(artist, limit).filter { it.artistKey.contains(key) }
    }

    /** Теги серверы не отдают в поиске: эту корзину питают Jamendo и Last.fm. */
    override suspend fun byTags(
        tags: List<String>,
        limit: Int,
        offset: Int,
    ): List<WaveTrack> = emptyList()

    override suspend fun find(hint: TrackHint): WaveTrack? =
        searchAll("${hint.artist} ${hint.title}", FIND_LIMIT).firstOrNull {
            it.artistKey == WaveTrack.keyOf(hint.artist) && it.titleKey == WaveTrack.keyOf(hint.title)
        }

    private suspend fun searchAll(
        query: String,
        limit: Int,
    ): List<WaveTrack> {
        val sources = connected.sources.value
        if (sources.isEmpty()) return emptyList()
        return coroutineScope {
            sources.map { source -> async { source.search(query, limit).tracks(source) } }.awaitAll().flatten()
        }
    }

    private fun PluginResult<SearchResult>.tracks(source: MusicSource): List<WaveTrack> =
        (this as? PluginResult.Ok)?.value?.tracks.orEmpty().mapNotNull { ref ->
            val provider = ref.externalIds.firstOrNull()?.provider ?: return@mapNotNull null
            ref.toWaveTrack(provider, origin)?.also { registry.remember(it.id, source, ref, origin) }
        }

    private companion object {
        const val FIND_LIMIT = 5
    }
}
