package dev.fuzz.engine.recommend.candidate

import dev.fuzz.core.model.TrackId
import dev.fuzz.core.model.wave.WaveSeed
import dev.fuzz.engine.recommend.model.TrackHint
import dev.fuzz.engine.recommend.model.WaveTrack

/** Индексы коллекции в памяти сессии: по ID, по артисту, по паре «артист + название». */
class LibraryIndex(
    tracks: List<WaveTrack>,
) {
    val all: List<WaveTrack> = tracks
    private val byId: Map<TrackId, WaveTrack> = tracks.associateBy { it.id }
    private val byArtist: Map<String, List<WaveTrack>> = tracks.groupBy { it.artistKey }
    private val byArtistTitle: Map<String, WaveTrack> =
        tracks.associateBy {
            it.artistKey + KEY_SEPARATOR + it.titleKey
        }

    operator fun get(id: TrackId): WaveTrack? = byId[id]

    fun byArtist(artistKey: String): List<WaveTrack> = byArtist[artistKey].orEmpty()

    fun hasArtist(artistKey: String): Boolean = artistKey in byArtist

    fun artistName(artistKey: String): String? = byArtist[artistKey]?.firstOrNull()?.artist

    fun find(hint: TrackHint): WaveTrack? =
        byArtistTitle[WaveTrack.keyOf(hint.artist) + KEY_SEPARATOR + WaveTrack.keyOf(hint.title)]

    fun contains(track: WaveTrack): Boolean =
        byArtistTitle.containsKey(
            track.artistKey + KEY_SEPARATOR + track.titleKey,
        )

    private companion object {
        const val KEY_SEPARATOR = "|"
    }
}

/** От чего стартовала волна: треки, артисты и теги зерна. У «Моей волны» всё пустое. */
class SeedInfo(
    val tracks: List<WaveTrack>,
    /** Ключ артиста → имя для запросов к графу похожести. */
    val artists: Map<String, String>,
    val tags: Map<String, Float>,
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && artists.isEmpty() && tags.isEmpty()

    companion object {
        val NONE = SeedInfo(emptyList(), emptyMap(), emptyMap())
        private const val GENRE_SEED_ARTISTS = 10
        private const val TAG_FLOOR = 0.2f

        fun resolve(
            seed: WaveSeed,
            library: LibraryIndex,
        ): SeedInfo =
            when (seed) {
                WaveSeed.MyWave -> NONE
                is WaveSeed.Track -> fromTracks(listOfNotNull(library[seed.trackId]))
                is WaveSeed.Album -> fromTracks(library.all.filter { it.albumId == seed.albumId })
                is WaveSeed.Playlist -> fromTracks(seed.trackIds.mapNotNull { library[it] })
                is WaveSeed.Artist -> fromArtist(seed.name, library)
                is WaveSeed.Genre -> fromGenre(seed.tag.lowercase(), library)
            }

        private fun fromArtist(
            name: String,
            library: LibraryIndex,
        ): SeedInfo {
            val key = WaveTrack.keyOf(name)
            val own = library.byArtist(key)
            return SeedInfo(own, mapOf(key to name), aggregateTags(own))
        }

        private fun fromGenre(
            tag: String,
            library: LibraryIndex,
        ): SeedInfo {
            val tracks = library.all.filter { (it.tags[tag] ?: 0f) >= TAG_FLOOR }
            val artists =
                tracks
                    .groupingBy { it.artistKey }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .take(GENRE_SEED_ARTISTS)
                    .associate { it.key to (library.artistName(it.key) ?: it.key) }
            return SeedInfo(emptyList(), artists, mapOf(tag to 1f))
        }

        private fun fromTracks(tracks: List<WaveTrack>): SeedInfo =
            SeedInfo(tracks, tracks.associate { it.artistKey to it.artist }, aggregateTags(tracks))

        private fun aggregateTags(tracks: List<WaveTrack>): Map<String, Float> {
            if (tracks.isEmpty()) return emptyMap()
            val sum = HashMap<String, Float>()
            tracks.forEach { t -> t.tags.forEach { (k, v) -> sum.merge(k, v, Float::plus) } }
            return sum.mapValues { it.value / tracks.size }
        }
    }
}
