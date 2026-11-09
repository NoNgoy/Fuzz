package dev.fuzz.core.data.wave

import dev.fuzz.core.domain.wave.WaveAnchor
import dev.fuzz.core.domain.wave.WaveFactor
import dev.fuzz.core.domain.wave.WaveReason
import dev.fuzz.core.domain.wave.WaveSource
import dev.fuzz.engine.recommend.model.Anchor
import dev.fuzz.engine.recommend.model.Bucket
import dev.fuzz.engine.recommend.model.Reason
import dev.fuzz.engine.recommend.model.ScoreTerm
import kotlin.math.roundToInt

/** Объяснение движка в терминах домена: экраны не знают про корзины и слагаемые оценки. */
internal fun Reason.toDomain(): WaveReason =
    WaveReason(
        source = bucket.toSource(),
        factor = term.toFactor(),
        anchor = anchor?.toDomain(),
        tags = tags,
        bpm = bpm?.roundToInt(),
    )

private fun Bucket.toSource(): WaveSource =
    when (this) {
        Bucket.FAVORITES -> WaveSource.Favorites
        Bucket.UNHEARD -> WaveSource.Unheard
        Bucket.SIMILAR -> WaveSource.Similar
        Bucket.OPEN_CATALOG -> WaveSource.OpenCatalog
        Bucket.PREVIEW -> WaveSource.Preview
    }

private fun ScoreTerm.toFactor(): WaveFactor =
    when (this) {
        ScoreTerm.TASTE_TAGS -> WaveFactor.Tags

        ScoreTerm.ARTIST -> WaveFactor.Artist

        ScoreTerm.AUDIO -> WaveFactor.Sound

        ScoreTerm.CONTEXT_FIT -> WaveFactor.Mood

        ScoreTerm.NOVELTY -> WaveFactor.Novelty

        ScoreTerm.POPULARITY -> WaveFactor.Popularity

        ScoreTerm.SOURCE -> WaveFactor.Link

        // Штрафы не бывают главной причиной; если все слагаемые малы, решил случай.
        ScoreTerm.RECENT, ScoreTerm.SKIPS, ScoreTerm.JITTER -> WaveFactor.Chance
    }

private fun Anchor.toDomain(): WaveAnchor =
    WaveAnchor(
        kind =
            when (kind) {
                Anchor.Kind.LIKED_ARTIST -> WaveAnchor.Kind.LikedArtist
                Anchor.Kind.LISTENED_ARTIST -> WaveAnchor.Kind.ListenedArtist
                Anchor.Kind.LIKED_TRACK -> WaveAnchor.Kind.LikedTrack
                Anchor.Kind.SEED -> WaveAnchor.Kind.Seed
            },
        name = name,
    )
