package dev.fuzz.engine.recommend.model

import dev.fuzz.core.model.TrackId

/** События, из которых копится вкус (раздел 4.2 ТЗ, пункт «Сигналы»). Пишутся локально, наружу не уходят. */
sealed interface WaveSignal {
    val trackId: TrackId
    val atMs: Long

    /** Прослушивание. [playedMs] меньше длительности и [completed] = false означает пропуск на этой секунде. */
    data class Listen(
        override val trackId: TrackId,
        override val atMs: Long,
        val playedMs: Long,
        val completed: Boolean,
    ) : WaveSignal

    data class Like(
        override val trackId: TrackId,
        override val atMs: Long,
    ) : WaveSignal

    data class Dislike(
        override val trackId: TrackId,
        override val atMs: Long,
    ) : WaveSignal

    /** Снятие лайка или дизлайка. */
    data class ClearReaction(
        override val trackId: TrackId,
        override val atMs: Long,
    ) : WaveSignal

    data class AddedToPlaylist(
        override val trackId: TrackId,
        override val atMs: Long,
    ) : WaveSignal
}
