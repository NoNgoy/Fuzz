package dev.fuzz.core.domain.wave

import dev.fuzz.core.model.TrackId

/**
 * Одно прослушивание. [completed] = false и [playedMs] меньше длительности значит пропуск на этой секунде.
 * История хранится только на устройстве (раздел 4.2 ТЗ, «Сигналы»).
 */
data class Listen(
    val trackId: TrackId,
    val startedAtMs: Long,
    val playedMs: Long,
    val completed: Boolean,
)
