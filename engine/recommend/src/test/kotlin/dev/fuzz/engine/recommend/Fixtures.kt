package dev.fuzz.engine.recommend

import dev.fuzz.core.model.TrackId
import dev.fuzz.engine.recommend.model.AudioFeatures
import dev.fuzz.engine.recommend.model.TrackHint
import dev.fuzz.engine.recommend.model.TrackOrigin
import dev.fuzz.engine.recommend.model.WaveTrack
import dev.fuzz.engine.recommend.model.Weighted
import dev.fuzz.engine.recommend.port.ExternalCatalog
import dev.fuzz.engine.recommend.port.SimilarityGraph
import dev.fuzz.engine.recommend.port.WaveLibrary

const val NOW = 1_800_000_000_000L

fun track(
    id: String,
    artist: String,
    tags: Map<String, Float> = emptyMap(),
    bpm: Float? = null,
    energy: Float = 0.5f,
    origin: TrackOrigin = TrackOrigin.Collection,
    language: String? = null,
) = WaveTrack(
    id = TrackId(id),
    title = "Song $id",
    artist = artist,
    durationMs = 200_000,
    origin = origin,
    tags = tags,
    language = language,
    audio = bpm?.let { AudioFeatures(it, energy, 0.5f) },
)

class FakeLibrary(
    val tracks: List<WaveTrack>,
) : WaveLibrary {
    override suspend fun tracks(): List<WaveTrack> = tracks
}

class FakeGraph(
    private val similar: Map<String, List<Pair<String, Float>>>,
    private val failing: Boolean = false,
) : SimilarityGraph {
    override suspend fun similarArtists(
        artist: String,
        limit: Int,
    ): List<Weighted<String>> {
        check(!failing) { "сеть недоступна" }
        return similar[artist].orEmpty().map { Weighted(it.first, it.second) }.take(limit)
    }

    override suspend fun similarTracks(
        track: TrackHint,
        limit: Int,
    ): List<Weighted<TrackHint>> = emptyList()
}

class FakePreviews(
    private val byArtist: Map<String, List<WaveTrack>>,
) : ExternalCatalog {
    override val origin = TrackOrigin.Preview

    override suspend fun byArtist(
        artist: String,
        limit: Int,
    ) = byArtist[artist].orEmpty().take(limit)

    override suspend fun byTags(
        tags: List<String>,
        limit: Int,
        offset: Int,
    ) = emptyList<WaveTrack>()

    override suspend fun find(hint: TrackHint): WaveTrack? = null
}

/** Небольшая коллекция: 20 артистов по 5 треков, четыре жанра. */
fun smallCollection(): List<WaveTrack> {
    val genres = listOf("metal" to 140f, "jazz" to 100f, "ambient" to 70f, "pop" to 115f)
    return (0 until 20).flatMap { a ->
        val (genre, bpm) = genres[a % genres.size]
        (0 until 5).map { t -> track("a${a}t$t", "Artist $a", mapOf(genre to 1f), bpm, energy = bpm / 160f) }
    }
}

/** Свой сервер или JS-плагин: ищет только по артисту. */
class FakeServer(
    private val byArtist: Map<String, List<WaveTrack>>,
) : ExternalCatalog {
    override val origin = TrackOrigin.OpenCatalog
    override val searchesByTags = false

    override suspend fun byArtist(
        artist: String,
        limit: Int,
    ) = byArtist[artist].orEmpty().take(limit)

    override suspend fun byTags(
        tags: List<String>,
        limit: Int,
        offset: Int,
    ) = emptyList<WaveTrack>()

    override suspend fun find(hint: TrackHint): WaveTrack? = null
}
