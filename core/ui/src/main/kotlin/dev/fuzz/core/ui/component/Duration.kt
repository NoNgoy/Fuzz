package dev.fuzz.core.ui.component

import java.util.Locale

private const val MS_IN_SECOND = 1000
private const val SECONDS_IN_MINUTE = 60
private const val MINUTES_IN_HOUR = 60

/** 3:07, 1:02:15. */
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / MS_IN_SECOND).coerceAtLeast(0)
    val seconds = totalSeconds % SECONDS_IN_MINUTE
    val totalMinutes = totalSeconds / SECONDS_IN_MINUTE
    val minutes = totalMinutes % MINUTES_IN_HOUR
    val hours = totalMinutes / MINUTES_IN_HOUR
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", totalMinutes, seconds)
    }
}
