package dev.fuzz.core.player.sound

/**
 * Громкость при плавных переходах: нарастает первые [fadeMs] трека и затихает последние [fadeMs].
 * Перемотка в середину сразу даёт полную громкость. Короткий трек не затихает больше чем наполовину длины.
 */
internal fun fadeVolume(
    positionMs: Long,
    durationMs: Long,
    fadeMs: Long,
): Float {
    if (fadeMs <= 0 || durationMs <= 0) return 1f
    val window = minOf(fadeMs, durationMs / 2).coerceAtLeast(1)
    val fadeIn = positionMs.toFloat() / window
    val fadeOut = (durationMs - positionMs).toFloat() / window
    return minOf(1f, fadeIn, fadeOut).coerceIn(0f, 1f)
}
