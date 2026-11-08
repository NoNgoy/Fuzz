package dev.fuzz.engine.recommend.candidate

import dev.fuzz.engine.recommend.model.Anchor
import dev.fuzz.engine.recommend.model.TrackHint
import dev.fuzz.engine.recommend.model.WaveTrack
import dev.fuzz.engine.recommend.model.Weighted
import dev.fuzz.engine.recommend.port.SimilarityGraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Артист, найденный через граф похожести, с накопленным весом и якорем для объяснения. */
data class FoundArtist(
    val key: String,
    val name: String,
    val score: Float,
    val anchor: Anchor,
)

/** Трек-подсказка из «похожих треков» с весом и якорем. */
data class FoundTrack(
    val hint: TrackHint,
    val score: Float,
    val anchor: Anchor,
)

/** Стартовая точка обхода графа: артист или трек со своим весом. */
data class ArtistAnchor(
    val name: String,
    val weight: Float,
    val anchor: Anchor,
)

data class TrackAnchor(
    val track: WaveTrack,
    val weight: Float,
    val anchor: Anchor,
)

/**
 * Обход графа похожести в ширину на всю сессию. Раунд 0 — прямые соседи якорей, каждый следующий
 * раунд раскрывает следующих лучших найденных артистов с затуханием по глубине. Именно это даёт
 * богатый поток: не 5 похожих треков на текущий, а растущее облако артистов вокруг всего вкуса.
 *
 * Ответы графа кэшируются на сессию; ошибка провайдера просто не добавляет соседей.
 */
class SimilarityExplorer(
    private val graph: SimilarityGraph,
    private val artistAnchors: List<ArtistAnchor>,
    private val trackAnchors: List<TrackAnchor>,
    private val settings: Settings = Settings(),
) {
    data class Settings(
        val neighbours: Int = 30,
        val expandPerRound: Int = 8,
        val hopDamping: Float = 0.55f,
        val similarTracks: Int = 30,
        val trackAnchorsPerRound: Int = 5,
    )

    private val mutex = Mutex()
    private val anchorKeys = artistAnchors.map { WaveTrack.keyOf(it.name) }.toSet()
    private val found = LinkedHashMap<String, FoundArtist>()
    private val depth = HashMap<String, Int>()
    private val expanded = HashSet<String>()
    private val hints = LinkedHashMap<String, FoundTrack>()
    private var artistRound = -1
    private var trackRound = -1

    /** Найденные артисты, лучшие первыми. Якоря сюда не входят: их треки дают другие корзины. */
    suspend fun artists(round: Int): List<FoundArtist> =
        mutex.withLock {
            while (artistRound < round) {
                artistRound++
                expandArtists(artistRound)
            }
            found.values.sortedByDescending { it.score }
        }

    suspend fun tracks(round: Int): List<FoundTrack> =
        mutex.withLock {
            while (trackRound < round) {
                trackRound++
                expandTracks(trackRound)
            }
            hints.values.sortedByDescending { it.score }
        }

    private suspend fun expandArtists(round: Int) {
        val frontier: List<Triple<String, Float, Anchor>> =
            if (round == 0) {
                artistAnchors.map { Triple(it.name, it.weight, it.anchor) }
            } else {
                found.values
                    .filter { it.key !in expanded }
                    .sortedByDescending { it.score }
                    .take(settings.expandPerRound)
                    .map { Triple(it.name, it.score, it.anchor) }
            }
        val results =
            coroutineScope {
                frontier
                    .map { (name, weight, anchor) ->
                        async {
                            Triple(name, weight, anchor) to safe { graph.similarArtists(name, settings.neighbours) }
                        }
                    }.awaitAll()
            }
        results.forEach { (origin, neighbours) ->
            val (name, weight, anchor) = origin
            val originKey = WaveTrack.keyOf(name)
            expanded += originKey
            val level = (depth[originKey] ?: 0) + 1
            neighbours.forEach { addArtist(it, weight * damping(level), anchor, level) }
        }
    }

    private fun addArtist(
        neighbour: Weighted<String>,
        weight: Float,
        anchor: Anchor,
        level: Int,
    ) {
        val key = WaveTrack.keyOf(neighbour.item)
        if (key in anchorKeys || key.isEmpty()) return
        val add = weight * neighbour.weight.coerceIn(0f, 1f)
        val current = found[key]
        found[key] =
            if (current == null) {
                FoundArtist(key, neighbour.item, add, anchor)
            } else {
                current.copy(score = current.score + add)
            }
        depth[key] = minOf(depth[key] ?: level, level)
    }

    private suspend fun expandTracks(round: Int) {
        val slice =
            trackAnchors
                .drop(round * settings.trackAnchorsPerRound)
                .take(settings.trackAnchorsPerRound)
        val results =
            coroutineScope {
                slice
                    .map { a ->
                        async {
                            a to
                                safe {
                                    graph.similarTracks(
                                        TrackHint(a.track.artist, a.track.title),
                                        settings.similarTracks,
                                    )
                                }
                        }
                    }.awaitAll()
            }
        results.forEach { (a, similar) ->
            similar.forEach { s ->
                val key = WaveTrack.keyOf(s.item.artist) + "|" + WaveTrack.keyOf(s.item.title)
                val add = a.weight * s.weight.coerceIn(0f, 1f)
                val current = hints[key]
                hints[key] = current?.copy(score = current.score + add) ?: FoundTrack(s.item, add, a.anchor)
            }
        }
    }

    private fun damping(level: Int): Float {
        var d = 1f
        repeat(level - 1) { d *= settings.hopDamping }
        return d
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // сбой любого провайдера только сужает волну
    private suspend fun <T> safe(block: suspend () -> List<T>): List<T> =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
}
