package dev.fuzz.core.data.wave

import dev.fuzz.core.domain.plugin.ArtistRef
import dev.fuzz.core.domain.plugin.MetadataProvider
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.Similar
import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.engine.recommend.model.TrackHint
import dev.fuzz.engine.recommend.model.Weighted
import dev.fuzz.engine.recommend.port.SimilarityGraph
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Граф похожести поверх провайдеров метаданных (Last.fm, ListenBrainz). Ответы провайдеров складываются
 * с их весами и ограничиваются единицей. Сбой одного провайдера просто убирает его вклад; без сети
 * отвечает из кэша или пустым списком. Кэш в памяти, постоянный кэш в Room добавляется отдельно.
 *
 * @param providers провайдеры с весами; вес масштабирует их оценки близости
 * @param mbidOf подсказка MBID артиста по имени (например, из локальной базы), нужна ListenBrainz
 */
class ProviderSimilarityGraph(
    private val providers: List<Pair<MetadataProvider, Float>>,
    private val mbidOf: suspend (String) -> ArtistRef? = { null },
    ttlMs: Long = DEFAULT_TTL_MS,
) : SimilarityGraph {
    private val artistCache = TtlCache<String, List<Weighted<String>>>(ttlMs)
    private val trackCache = TtlCache<TrackHint, List<Weighted<TrackHint>>>(ttlMs)

    override suspend fun similarArtists(artist: String, limit: Int): List<Weighted<String>> {
        val key = artist.trim().lowercase()
        artistCache.get(key)?.let { return it.take(limit) }
        val ref = mbidOf(artist) ?: ArtistRef(artist)
        val merged =
            merge(ref.name, limit) { provider ->
                (provider.similarArtists(ref, limit) as? PluginResult.Ok)?.value.orEmpty().map {
                    it.mapItem { a ->
                        a.name
                    }
                }
            }
        if (merged.isNotEmpty()) artistCache.put(key, merged)
        return merged.take(limit)
    }

    override suspend fun similarTracks(track: TrackHint, limit: Int): List<Weighted<TrackHint>> {
        trackCache.get(track)?.let { return it.take(limit) }
        val ref = TrackRef(title = track.title, artist = track.artist)
        val results =
            coroutineScope {
                providers.map { (provider, weight) ->
                    async {
                        (provider.similarTracks(ref, limit) as? PluginResult.Ok)?.value.orEmpty()
                            .map { Weighted(TrackHint(it.item.artist, it.item.title), it.score * weight) }
                    }
                }.map { it.await() }
            }
        val merged =
            results.flatten()
                .groupBy { it.item.artist.trim().lowercase() to it.item.title.trim().lowercase() }
                .map { (_, group) ->
                    Weighted(group.first().item, group.sumOf { it.weight.toDouble() }.coerceAtMost(1.0).toFloat())
                }
                .sortedByDescending { it.weight }
        if (merged.isNotEmpty()) trackCache.put(track, merged)
        return merged.take(limit)
    }

    private suspend fun merge(
        selfName: String,
        limit: Int,
        fetch: suspend (MetadataProvider) -> List<Similar<String>>,
    ): List<Weighted<String>> {
        val results =
            coroutineScope {
                providers.map { (provider, weight) ->
                    async { fetch(provider).map { Weighted(it.item, it.score * weight) } }
                }.map { it.await() }
            }
        val self = selfName.trim().lowercase()
        return results.flatten()
            .filter { it.item.trim().lowercase() != self }
            .groupBy { it.item.trim().lowercase() }
            .map { (_, group) ->
                Weighted(group.first().item, group.sumOf { it.weight.toDouble() }.coerceAtMost(1.0).toFloat())
            }
            .sortedByDescending { it.weight }
            .take(limit * OVERFETCH)
    }

    private fun <T, R> Similar<T>.mapItem(transform: (T) -> R) = Similar(transform(item), score)

    private companion object {
        const val DEFAULT_TTL_MS = 24 * 60 * 60 * 1000L
        const val OVERFETCH = 2
    }
}
