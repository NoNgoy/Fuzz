package dev.fuzz.engine.recommend.candidate

import dev.fuzz.engine.recommend.model.Anchor
import dev.fuzz.engine.recommend.model.Bucket
import dev.fuzz.engine.recommend.model.Candidate
import dev.fuzz.engine.recommend.model.WaveTrack
import dev.fuzz.engine.recommend.port.ExternalCatalog
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Каталог, который ищет только по имени артиста (свои серверы, JS-плагины): треки любимых артистов,
 * которых нет на телефоне, и треки найденных в графе похожих артистов. Каждый раунд берёт следующую порцию.
 * Без графа похожести остаются хотя бы любимые артисты, поэтому волна не сводится к скачанному.
 */
class ArtistCatalogGenerator(
    private val catalog: ExternalCatalog,
    override val bucket: Bucket = Bucket.OPEN_CATALOG,
) : CandidateGenerator {
    override suspend fun candidates(
        ctx: WaveContext,
        limit: Int,
    ): List<Candidate> {
        val slice = artists(ctx).drop(ctx.round * ARTISTS_PER_ROUND).take(ARTISTS_PER_ROUND)
        if (slice.isEmpty()) return emptyList()
        val found =
            coroutineScope {
                slice
                    .map { a ->
                        async {
                            catalog.byArtist(a.name, TRACKS_PER_ARTIST).map {
                                Candidate(it, bucket, a.strength, a.anchor)
                            }
                        }
                    }.awaitAll()
                    .flatten()
            }
        return found
            .filter {
                it.track.origin == catalog.origin && !ctx.library.contains(it.track) &&
                    it.track.id !in ctx.exclude && it.track.id !in ctx.pooled &&
                    !ctx.profile.isDisliked(it.track.id)
            }.distinctBy { it.track.id }
            .take(limit)
    }

    /** Сначала зерно и свои артисты, потом найденные похожие; без повторов по ключу артиста. */
    private suspend fun artists(ctx: WaveContext): List<ArtistPick> {
        val seed = ctx.seedInfo.artists.map { (_, name) -> ArtistPick(name, 1f, Anchor(Anchor.Kind.SEED, name)) }
        val explorer = ctx.explorer
        val own =
            explorer
                ?.anchors
                .orEmpty()
                .sortedByDescending { it.weight }
                .map { ArtistPick(it.name, OWN_ARTIST_STRENGTH, it.anchor) }
        val similar =
            explorer?.artists(ctx.round).orEmpty().let { found ->
                val top = found.firstOrNull()?.score?.coerceAtLeast(1f) ?: 1f
                found.map { ArtistPick(it.name, (it.score / top).coerceIn(0f, 1f), it.anchor) }
            }
        return (seed + own + similar).distinctBy { WaveTrack.keyOf(it.name) }
    }

    private data class ArtistPick(
        val name: String,
        val strength: Float,
        val anchor: Anchor,
    )

    private companion object {
        const val ARTISTS_PER_ROUND = 10
        const val TRACKS_PER_ARTIST = 4
        const val OWN_ARTIST_STRENGTH = 0.8f
    }
}
