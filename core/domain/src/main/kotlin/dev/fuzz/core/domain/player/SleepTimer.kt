package dev.fuzz.core.domain.player

import kotlinx.coroutines.flow.StateFlow

sealed interface SleepTimerState {
    data object Off : SleepTimerState

    /** Пауза через [remainingMs]; значение обновляется раз в секунду. */
    data class Countdown(
        val remainingMs: Long,
    ) : SleepTimerState

    /** Пауза, когда закончится текущий трек. */
    data object EndOfTrack : SleepTimerState
}

/** Таймер сна: ставит плеер на паузу через заданное время или в конце трека. Один на приложение. */
interface SleepTimer {
    val state: StateFlow<SleepTimerState>

    fun start(minutes: Int)

    fun stopAfterTrack()

    fun cancel()
}
