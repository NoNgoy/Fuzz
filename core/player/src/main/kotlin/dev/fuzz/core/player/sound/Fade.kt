package dev.fuzz.core.player.sound

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Когда начинать наложение: за [fadeMs] до конца трека, если есть следующий и трек не короче двух переходов. */
internal fun crossfadeDue(
    positionMs: Long,
    durationMs: Long,
    fadeMs: Long,
    leadMs: Long = 0,
): Boolean {
    if (fadeMs <= 0 || durationMs <= 0 || durationMs < fadeMs * 2) return false
    return durationMs - positionMs <= fadeMs + leadMs
}

/**
 * Громкость при наложении на доле перехода [t] 0..1: равная мощность, чтобы середина не проваливалась.
 * Первое значение — уходящий трек, второе — новый.
 */
internal fun crossfadeVolumes(t: Float): Pair<Float, Float> {
    val x = t.coerceIn(0f, 1f) * HALF_PI
    return cos(x) to sin(x)
}

private const val HALF_PI = (PI / 2).toFloat()
