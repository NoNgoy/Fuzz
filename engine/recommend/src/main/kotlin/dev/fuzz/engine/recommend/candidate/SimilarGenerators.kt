package dev.fuzz.engine.recommend.candidate

import dev.fuzz.engine.recommend.model.Anchor
import dev.fuzz.engine.recommend.model.Bucket
import dev.fuzz.engine.recommend.model.Candidate
import dev.fuzz.engine.recommend.model.TrackOrigin
import dev.fuzz.engine.recommend.model.WaveTrack
import dev.fuzz.engine.recommend.port.ExternalCatalog
import dev.fuzz.engine.recommend.profile.TasteProfile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * «Похожие по Last.fm/ListenBrainz» в коллекции: треки найденных в графе артистов и совпавшие
 * «похожие треки». Для волны по артисту сюда же попадают треки самого артиста. Без графа
 * (нет провайдеров, нет кэша) соседей ищем по тегам внутри коллекции.
 */
class SimilarGenerator : CandidateGenerator {
    override val bucket = Bucket.SIMILAR

    override suspend fun candidates(
        ctx: WaveContext,
        limit: Int,
    ): List<Candidate> {
        val out = LinkedHashMap<String, Candidate>()

        fun offer(c: Candidate) {
            if (c.track.id in ctx.exclude || ctx.profile.isDisliked(c.track.id)) return
            val prev = out[c.track.id.value]
            if (prev == null || prev.strength < c.strength) out[c.track.id.value] = c
        }
        seedArtistTracks(ctx).forEach(::offer)
        val explorer = ctx.explorer
        if (explorer != null) {
            val artists = explorer.artists(ctx.round)
            val top = artists.firstOrNull()?.score ?: 1f
            artists.forEach { a ->
                ctx.library.byArtist(a.key).forEach { offer(Candidate(it, bucket, a.score / top, a.anchor)) }
            }
            val tracks = explorer.tracks(ctx.round)
            val topTrack = tracks.firstOrNull()?.score ?: 1f
            tracks.forEach { t ->
                ctx.library.find(t.hint)?.let { offer(Candidate(it, bucket, t.score / topTrack, t.anchor)) }
            }
        }
        if (out.size < limit / 2) tagNeighbours(ctx, limit).forEach(::offer)
        return out.values.sortedByDescending { it.strength }.take(limit)
    }

    private fun seedArtistTracks(ctx: WaveContext): List<Candidate> =
        ctx.seedInfo.artists.flatMap { (key, name) ->
            ctx.library.byArtist(key).map { Candidate(it, bucket, 1f, Anchor(Anchor.Kind.SEED, name)) }
        }

    /** Запасной путь офлайн: артисты коллекции, чьи теги ближе всего ко вкусу или к зерну. */
    private fun tagNeighbours(
        ctx: WaveContext,
        limit: Int,
    ): List<Candidate> {
        val target = ctx.seedInfo.tags.ifEmpty { ctx.profile.tags.filterValues { it > 0f } }
        if (target.isEmpty()) return emptyList()
        val norm = TasteProfile.norm(target)
        val own = ctx.seedInfo.artists.keys + ctx.profile.topArtists(TOP_OWN_ARTISTS).map { it.first }
        return ctx.library.all
            .asSequence()
            .filter { it.artistKey !in own }
            .map { it to TasteProfile.cosine(it.tags, target, norm) }
            .filter { it.second > MIN_TAG_SIMILARITY }
            .sortedByDescending { it.second }
            .take(limit)
            .map { (track, sim) -> Candidate(track, bucket, sim * TAG_FALLBACK_STRENGTH) }
            .toList()
    }

    private companion object {
        const val TOP_OWN_ARTISTS = 5
        const val MIN_TAG_SIMILARITY = 0.3f
        const val TAG_FALLBACK_STRENGTH = 0.6f
    }
}

/**
 * «Превью вне коллекции»: треки найденных в графе артистов и «похожие треки», которых нет в
 * коллекции, через каталог превью (Deezer). Каждый раунд берёт следующую порцию артистов.
 */
class PreviewGenerator(
    private val catalog: ExternalCatalog,
) : CandidateGenerator {
    override val bucket = Bucket.PREVIEW

    override suspend fun candidates(
        ctx: WaveContext,
        limit: Int,
    ): List<Candidate> {
        val explorer = ctx.explorer ?: return emptyList()
        val artists = explorer.artists(ctx.round)
        val seedArtists =
            ctx.seedInfo.artists.map { (key, name) ->
                FoundArtistRef(key, name, 1f, Anchor(Anchor.Kind.SEED, name))
            }
        val slice =
            (seedArtists + artists.map { FoundArtistRef(it.key, it.name, it.score, it.anchor) })
                .drop(ctx.round * ARTISTS_PER_ROUND)
                .take(ARTISTS_PER_ROUND)
        val top = artists.firstOrNull()?.score?.coerceAtLeast(1f) ?: 1f
        val hints =
            explorer
                .tracks(ctx.round)
                .filter { ctx.library.find(it.hint) == null }
                .drop(ctx.round * HINTS_PER_ROUND)
                .take(HINTS_PER_ROUND)
        val found =
            coroutineScope {
                val byArtist =
                    slice.map { a ->
                        async {
                            catalog.byArtist(a.name, TRACKS_PER_ARTIST).map {
                                Candidate(it, bucket, (a.score / top).coerceAtMost(1f), a.anchor)
                            }
                        }
                    }
                val byHint =
                    hints.map { h ->
                        async { listOfNotNull(catalog.find(h.hint)?.let { Candidate(it, bucket, 1f, h.anchor) }) }
                    }
                (byArtist + byHint).awaitAll().flatten()
            }
        return found
            .filter {
                it.track.origin == TrackOrigin.Preview && !ctx.library.contains(it.track) &&
                    it.track.id !in ctx.exclude &&
                    it.track.id !in ctx.pooled
            }
            .distinctBy { it.track.id }
            .take(limit)
    }

    private data class FoundArtistRef(
        val key: String,
        val name: String,
        val score: Float,
        val anchor: Anchor,
    )

    private companion object {
        const val ARTISTS_PER_ROUND = 12
        const val HINTS_PER_ROUND = 15
        const val TRACKS_PER_ARTIST = 3
    }
}

/** «Jamendo по тегам»: открытый каталог по тегам вкуса, зерна или занятия, страница за страницей. */
class OpenCatalogGenerator(
    private val catalog: ExternalCatalog,
    private val contextTags: (WaveContext) -> List<String> = { emptyList() },
) : CandidateGenerator {
    override val bucket = Bucket.OPEN_CATALOG

    override suspend fun candidates(
        ctx: WaveContext,
        limit: Int,
    ): List<Candidate> {
        val tags =
            (
                ctx.seedInfo.tags.entries.sortedByDescending { it.value }.map { it.key } + contextTags(ctx) +
                    ctx.profile.topTags(TOP_TAGS)
            )
                .distinct()
                .take(TOP_TAGS)
        if (tags.isEmpty()) return emptyList()
        return catalog
            .byTags(tags, limit, ctx.round * limit)
            .filter { it.id !in ctx.exclude && it.id !in ctx.pooled && !ctx.library.contains(it) }
            .map { track ->
                val sim = TasteProfile.cosine(track.tags, tags.associateWith { 1f })
                Candidate(track, bucket, sim.coerceIn(0f, 1f), null)
            }
    }

    private companion object {
        const val TOP_TAGS = 5
    }
}
