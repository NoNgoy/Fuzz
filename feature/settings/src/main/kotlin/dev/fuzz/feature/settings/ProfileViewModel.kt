package dev.fuzz.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fuzz.core.domain.stats.ListeningStats
import dev.fuzz.core.domain.stats.ListeningStatsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        stats: ListeningStatsRepository,
    ) : ViewModel() {
        val stats: StateFlow<ListeningStats> =
            stats
                .observeLastMonth()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ListeningStats())

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

private const val MS_IN_HOUR = 3_600_000.0
private const val WHOLE_HOURS_FROM = 10

/** «1,5» до десяти часов и «12» дальше: мелкие числа без дробей выглядели бы как нули. */
internal fun formatHours(ms: Long): String {
    val hours = ms / MS_IN_HOUR
    return if (hours >= WHOLE_HOURS_FROM) {
        hours.toInt().toString()
    } else {
        "%.1f".format(java.util.Locale.ROOT, hours).removeSuffix(".0").replace('.', ',')
    }
}
