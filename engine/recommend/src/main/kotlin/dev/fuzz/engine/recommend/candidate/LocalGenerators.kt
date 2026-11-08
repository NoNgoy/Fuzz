package dev.fuzz.engine.recommend.candidate

import dev.fuzz.core.model.Reaction
import dev.fuzz.engine.recommend.model.Anchor
import dev.fuzz.engine.recommend.model.Bucket
import dev.fuzz.engine.recommend.model.Candidate
import dev.fuzz.engine.recommend.model.WaveTrack
import dev.fuzz.engine.recommend.profile.TasteProfile

/**
 * Релевантность трека зерну волны: для «Моей волны» всегда 1, для волны по артисту, треку,
 * жанру или плейлисту — близость по артисту и тегам. Нерелевантные зерну треки корзины не берут.
 */
internal fun seedRelevance(
    track: WaveTrack,
    ctx: WaveContext,
): Float {
    val seed = ctx.seedInfo
    if (seed.isEmpty) return 1f
    if (track.artistKey in seed.artists) return 1f
    return TasteProfile.cosine(track.tags, seed.tags)
}

private const val SEED_RELEVANCE_FLOOR = 0.35f

/** «Свои любимые»: лайкнутое и часто дослушанное, с затухающей аффинити. */
class FavoritesGenerator : CandidateGenerator {
    override val bucket = Bucket.FAVORITES
    override val isLocal = true

    override suspend fun candidates(
        ctx: WaveContext,
        limit: Int,
    ): List<Candidate> {
        val scored =
            ctx.library.all
                .asSequence()
                .filter { it.id !in ctx.exclude && it.id !in ctx.pooled }
                .mapNotNull { track ->
                    val stats = ctx.profile.stats(track.id)
                    val liked = stats.reaction == Reaction.Like
                    val affinity = stats.affinity + if (liked) LIKE_FLOOR else 0f
                    val relevance = seedRelevance(track, ctx)
                    if (affinity <= 0f || stats.reaction == Reaction.Dislike || relevance < SEED_RELEVANCE_FLOOR) {
                        null
                    } else {
                        Triple(track, affinity * relevance, liked)
                    }
                }.sortedByDescending { it.second }
                .take(limit)
                .toList()
        val top = scored.firstOrNull()?.second ?: return emptyList()
        return scored.map { (track, value, liked) ->
            Candidate(
                track = track,
                bucket = bucket,
                strength = value / top,
                anchor = if (liked) Anchor(Anchor.Kind.LIKED_TRACK, track.title) else null,
            )
        }
    }

    private companion object {
        const val LIKE_FLOOR = 1f
    }
}

/** «Непрослушанное из коллекции»: ни разу не игранное, ближайшее ко вкусу или к зерну. */
class UnheardGenerator : CandidateGenerator {
    override val bucket = Bucket.UNHEARD
    override val isLocal = true

    override suspend fun candidates(
        ctx: WaveContext,
        limit: Int,
    ): List<Candidate> =
        ctx.library.all
            .asSequence()
            .filter {
                it.id !in ctx.exclude && it.id !in ctx.pooled && !ctx.profile.stats(it.id).heard &&
                    !ctx.profile.isDisliked(it.id)
            }
            .mapNotNull { track ->
                val relevance = seedRelevance(track, ctx)
                if (relevance < SEED_RELEVANCE_FLOOR) return@mapNotNull null
                val taste = ctx.profile.tagCosine(track.tags)
                track to (relevance + taste) / 2f
            }.sortedByDescending { it.second }
            .take(limit)
            .map { (track, value) -> Candidate(track, bucket, strength = (value + 1f) / 2f) }
            .toList()
}
