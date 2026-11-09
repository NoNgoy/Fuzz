package dev.fuzz.core.model

enum class RepeatMode { Off, One, All }

/** Состояние плеера, общее для экранов, уведомления, Android Auto и часов. */
data class PlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
    /** Почему плеер не смог играть текущий трек; null, пока ошибки нет. */
    val error: String? = null,
) {
    val hasNext: Boolean get() = repeatMode != RepeatMode.Off || currentIndex in 0 until queue.lastIndex
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
