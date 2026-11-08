package dev.fuzz.core.data.wave

import dev.fuzz.core.domain.plugin.MusicSource
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.SearchResult
import dev.fuzz.core.domain.plugin.TagSearchable
import dev.fuzz.engine.recommend.model.TrackHint
import dev.fuzz.engine.recommend.model.TrackOrigin
import dev.fuzz.engine.recommend.model.WaveTrack
import dev.fuzz.engine.recommend.port.ExternalCatalog

/**
 * Внешний каталог вне коллекции поверх [MusicSource]: Jamendo ([TrackOrigin.OpenCatalog]) или превью Deezer
 * ([TrackOrigin.Preview]). [provider] — ключ внешнего ID в TrackRef (`jamendo`, `deezer`).
 * Выданные треки запоминаются в [registry], чтобы плеер потом получил для них ссылку на поток.
 * Поиск по тегам работает, если источник реализует [TagSearchable]; иначе возвращается пустой список.
 */
class SourceExternalCatalog(
    private val source: MusicSource,
    override val origin: TrackOrigin,
    private val provider: String,
    private val registry: ExternalTrackRegistry? = null,
) : ExternalCatalog {
    override suspend fun byArtist(artist: String, limit: Int): List<WaveTrack> =
        source.search(artist, limit).tracks()
            .filter { it.artist.contains(artist, ignoreCase = true) }

    override suspend fun byTags(tags: List<String>, limit: Int, offset: Int): List<WaveTrack> {
        val searchable = source as? TagSearchable ?: return emptyList()
        return searchable.byTags(tags, limit, offset).tracks()
            .map { it.copy(tags = tags.associateWith { 1f }) }
    }

    override suspend fun find(hint: TrackHint): WaveTrack? =
        source.search("${hint.artist} ${hint.title}", FIND_LIMIT).tracks().firstOrNull {
            it.artistKey == WaveTrack.keyOf(hint.artist) && it.titleKey == WaveTrack.keyOf(hint.title)
        }

    private fun PluginResult<SearchResult>.tracks(): List<WaveTrack> =
        (this as? PluginResult.Ok)?.value?.tracks.orEmpty().mapNotNull { ref ->
            ref.toWaveTrack(provider, origin)?.also { registry?.remember(it.id, source, ref, origin) }
        }

    private companion object {
        const val FIND_LIMIT = 5
    }
}
