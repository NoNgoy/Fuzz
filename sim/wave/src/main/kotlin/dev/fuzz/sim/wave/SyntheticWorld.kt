package dev.fuzz.sim.wave

import dev.fuzz.core.model.TrackId
import dev.fuzz.engine.recommend.model.AudioFeatures
import dev.fuzz.engine.recommend.model.TrackHint
import dev.fuzz.engine.recommend.model.TrackOrigin
import dev.fuzz.engine.recommend.model.WaveTrack
import dev.fuzz.engine.recommend.model.Weighted
import dev.fuzz.engine.recommend.port.ExternalCatalog
import dev.fuzz.engine.recommend.port.SimilarityGraph
import dev.fuzz.engine.recommend.port.WaveLibrary
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Жанровый кластер синтетического мира: теги и типичные аудиопризнаки. */
data class Cluster(
    val name: String,
    val tags: List<String>,
    val bpm: Float,
    val energy: Float,
    val valence: Float,
)

/** Синтетический артист: кластер и треки (в коллекции или только во внешнем мире). */
data class SimArtist(
    val name: String,
    val cluster: Int,
    val inCollection: Boolean,
    val tracks: List<WaveTrack>,
)

/**
 * Синтетический музыкальный мир для проверки требований к волне (раздел 9.1 ТЗ) без сети.
 * Коллекция ~600 треков, вокруг неё сотни артистов вне коллекции (превью) и открытый каталог.
 * Похожесть артистов задаётся кластерами: свой кластер — сильно, соседний — слабо.
 */
class SyntheticWorld(
    seed: Long = 42,
    collectionArtists: Int = 60,
    externalArtists: Int = 400,
    openCatalogTracks: Int = 2000,
    /** Задержка «сетевых» портов, чтобы проверять старт и пересчёт на честном времени. */
    val networkDelayMs: Long = 40,
) {
    private val random = Random(seed)

    val clusters: List<Cluster> =
        listOf(
            Cluster("nu metal", listOf("nu metal", "metal", "alternative metal", "aggressive"), 135f, 0.9f, 0.3f),
            Cluster("post-rock", listOf("post-rock", "instrumental", "ambient"), 100f, 0.45f, 0.4f),
            Cluster("indie", listOf("indie", "indie rock", "alternative"), 120f, 0.6f, 0.6f),
            Cluster("hip hop", listOf("hip hop", "rap"), 92f, 0.7f, 0.5f),
            Cluster("electronic", listOf("electronic", "edm", "dance", "energetic"), 128f, 0.85f, 0.7f),
            Cluster("ambient", listOf("ambient", "drone", "sleep", "calm"), 70f, 0.15f, 0.4f),
            Cluster("jazz", listOf("jazz", "smooth jazz", "chill"), 105f, 0.35f, 0.6f),
            Cluster("pop", listOf("pop", "happy", "feel good"), 115f, 0.65f, 0.85f),
            Cluster("punk", listOf("punk", "punk rock", "energetic"), 170f, 0.95f, 0.6f),
            Cluster("classical", listOf("classical", "piano", "instrumental"), 80f, 0.25f, 0.5f),
            Cluster("russian rock", listOf("russian rock", "rock", "post-punk"), 125f, 0.7f, 0.4f),
            Cluster("lo-fi", listOf("lo-fi", "chill", "instrumental", "downtempo"), 85f, 0.3f, 0.55f),
        )

    val artists: List<SimArtist>
    val collection: List<WaveTrack>
    val openCatalog: List<WaveTrack>
    private val byName: Map<String, SimArtist>
    private val clusterOf = HashMap<TrackId, Int>()

    init {
        var n = 0
        artists =
            (0 until collectionArtists + externalArtists).map { i ->
                val cluster = i % clusters.size
                val inCollection = i < collectionArtists
                val name = "Artist $i (${clusters[cluster].name})"
                val count = if (inCollection) random.nextInt(MIN_TRACKS, MAX_TRACKS) else EXTERNAL_TRACKS
                val origin = if (inCollection) TrackOrigin.Collection else TrackOrigin.Preview
                SimArtist(name, cluster, inCollection, (0 until count).map { track(n++, name, cluster, origin) })
            }
        collection = artists.filter { it.inCollection }.flatMap { it.tracks }
        openCatalog =
            (0 until openCatalogTracks).map {
                track(n++, "Jamendo ${it % JAMENDO_ARTISTS}", it % clusters.size, TrackOrigin.OpenCatalog)
            }
        byName = artists.associateBy { it.name }
    }

    fun clusterOf(id: TrackId): Int = clusterOf.getValue(id)

    val library: WaveLibrary =
        object : WaveLibrary {
            override suspend fun tracks(): List<WaveTrack> = collection
        }

    val graph: SimilarityGraph =
        object : SimilarityGraph {
            override suspend fun similarArtists(
                artist: String,
                limit: Int,
            ): List<Weighted<String>> {
                delay(networkDelayMs)
                val me = byName[artist] ?: return emptyList()
                return artists
                    .asSequence()
                    .filter { it.name != artist }
                    .map { Weighted(it.name, affinity(me, it)) }
                    .filter { it.weight > 0f }
                    .sortedByDescending { it.weight }
                    .take(limit)
                    .toList()
            }

            override suspend fun similarTracks(
                track: TrackHint,
                limit: Int,
            ): List<Weighted<TrackHint>> {
                delay(networkDelayMs)
                val me = byName[track.artist] ?: return emptyList()
                return artists
                    .filter { it.cluster == me.cluster && it.name != me.name }
                    .flatMap { a -> a.tracks.take(2).map { Weighted(TrackHint(a.name, it.title), affinity(me, a)) } }
                    .sortedByDescending { it.weight }
                    .take(limit)
            }
        }

    val previews: ExternalCatalog =
        object : ExternalCatalog {
            override val origin = TrackOrigin.Preview

            override suspend fun byArtist(
                artist: String,
                limit: Int,
            ): List<WaveTrack> {
                delay(networkDelayMs)
                return byName[artist]?.takeIf { !it.inCollection }?.tracks?.take(limit).orEmpty()
            }

            override suspend fun byTags(
                tags: List<String>,
                limit: Int,
                offset: Int,
            ): List<WaveTrack> = emptyList()

            override suspend fun find(hint: TrackHint): WaveTrack? {
                delay(networkDelayMs)
                return byName[hint.artist]?.takeIf { !it.inCollection }?.tracks?.firstOrNull { it.title == hint.title }
            }
        }

    val jamendo: ExternalCatalog =
        object : ExternalCatalog {
            override val origin = TrackOrigin.OpenCatalog

            override suspend fun byArtist(
                artist: String,
                limit: Int,
            ): List<WaveTrack> = emptyList()

            override suspend fun byTags(
                tags: List<String>,
                limit: Int,
                offset: Int,
            ): List<WaveTrack> {
                delay(networkDelayMs)
                return openCatalog
                    .filter { t -> tags.any { it in t.tags } }
                    .drop(offset)
                    .take(limit)
            }

            override suspend fun find(hint: TrackHint): WaveTrack? = null
        }

    private fun affinity(
        a: SimArtist,
        b: SimArtist,
    ): Float =
        when {
            a.cluster == b.cluster -> {
                SAME_CLUSTER -
                    (a.name.hashCode() xor b.name.hashCode()).mod(SPREAD) / SPREAD_SCALE
            }

            (a.cluster - b.cluster).mod(clusters.size) == 1 || (b.cluster - a.cluster).mod(clusters.size) == 1 -> {
                NEIGHBOUR
            }

            else -> {
                0f
            }
        }

    private fun track(
        n: Int,
        artist: String,
        cluster: Int,
        origin: TrackOrigin,
    ): WaveTrack {
        val c = clusters[cluster]
        val id = TrackId("t$n")
        clusterOf[id] = cluster
        val tags =
            c.tags.mapIndexed { i, tag ->
                tag to
                    (1f - i * TAG_STEP) * (TAG_BASE + random.nextFloat() * TAG_NOISE)
            }.toMap()
        return WaveTrack(
            id = id,
            title = "Song $n",
            artist = artist,
            album = "$artist — album ${n % ALBUMS}",
            durationMs = random.nextLong(MIN_DURATION_MS, MAX_DURATION_MS),
            origin = origin,
            tags = tags,
            audio =
                AudioFeatures(
                    bpm = c.bpm + (random.nextFloat() - 0.5f) * BPM_NOISE,
                    energy = (c.energy + (random.nextFloat() - 0.5f) * UNIT_NOISE).coerceIn(0f, 1f),
                    valence = (c.valence + (random.nextFloat() - 0.5f) * UNIT_NOISE).coerceIn(0f, 1f),
                ),
            popularity = random.nextFloat(),
        )
    }

    private companion object {
        const val MIN_TRACKS = 6
        const val MAX_TRACKS = 15
        const val EXTERNAL_TRACKS = 10
        const val JAMENDO_ARTISTS = 300
        const val ALBUMS = 3
        const val SAME_CLUSTER = 0.95f
        const val NEIGHBOUR = 0.3f
        const val SPREAD = 40
        const val SPREAD_SCALE = 100f
        const val TAG_STEP = 0.15f
        const val TAG_BASE = 0.7f
        const val TAG_NOISE = 0.3f
        const val MIN_DURATION_MS = 150_000L
        const val MAX_DURATION_MS = 300_000L
        const val BPM_NOISE = 20f
        const val UNIT_NOISE = 0.2f
    }
}
