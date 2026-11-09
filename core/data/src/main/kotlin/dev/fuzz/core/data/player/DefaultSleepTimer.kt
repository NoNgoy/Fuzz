package dev.fuzz.core.data.player

import dev.fuzz.core.data.sync.Clock
import dev.fuzz.core.domain.player.PlayerController
import dev.fuzz.core.domain.player.SleepTimer
import dev.fuzz.core.domain.player.SleepTimerState
import dev.fuzz.core.model.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Таймер живёт в процессе приложения рядом с сервисом плеера, а не в экране: закрытый плеер
 * или заблокированный телефон его не сбрасывают. Время считается от часов, а не суммой задержек,
 * поэтому часовой таймер не уплывает.
 */
@Singleton
internal class DefaultSleepTimer(
    private val player: PlayerController,
    private val clock: Clock,
    private val scope: CoroutineScope,
) : SleepTimer {
    @Inject
    constructor(player: PlayerController, clock: Clock) :
        this(player, clock, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    private val mutable = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    override val state: StateFlow<SleepTimerState> = mutable.asStateFlow()

    private var job: Job? = null

    override fun start(minutes: Int) {
        require(minutes > 0) { "minutes must be positive" }
        val endsAt = clock.nowMs() + minutes * MINUTE_MS
        mutable.value = SleepTimerState.Countdown(minutes * MINUTE_MS)
        replaceJob {
            var left = endsAt - clock.nowMs()
            while (left > 0) {
                mutable.value = SleepTimerState.Countdown(left)
                delay(minOf(left, TICK_MS))
                left = endsAt - clock.nowMs()
            }
            finish(rewind = false)
        }
    }

    override fun stopAfterTrack() {
        mutable.value = SleepTimerState.EndOfTrack
        replaceJob {
            var previous = player.state.value
            player.state.first { now -> trackEnded(previous, now).also { previous = now } }
            finish(rewind = true)
        }
    }

    override fun cancel() {
        job?.cancel()
        job = null
        mutable.value = SleepTimerState.Off
    }

    private fun replaceJob(block: suspend CoroutineScope.() -> Unit) {
        job?.cancel()
        job = scope.launch(block = block)
    }

    /** После смены трека следующий уже успел начаться: возвращаем его в начало, чтобы утром играл целиком. */
    private fun finish(rewind: Boolean) {
        val playback = player.state.value
        if (playback.isPlaying) {
            player.pause()
            if (rewind) player.seekTo(0)
        }
        mutable.value = SleepTimerState.Off
        job = null
    }

    private fun trackEnded(
        before: PlaybackState,
        now: PlaybackState,
    ): Boolean {
        val changed = before.currentTrack?.id != now.currentTrack?.id || before.currentIndex != now.currentIndex
        // Повтор одного трека: id тот же, но позиция прыгнула с конца в начало.
        val wrapped =
            before.durationMs > 0 &&
                before.positionMs >= before.durationMs - NEAR_END_MS &&
                now.positionMs < NEAR_END_MS
        // Очередь кончилась сама: плеер остановился в конце трека.
        val stopped =
            before.isPlaying && !now.isPlaying && now.durationMs > 0 && now.positionMs >= now.durationMs - NEAR_END_MS
        return before.currentTrack != null && (changed || wrapped || stopped)
    }

    private companion object {
        const val MINUTE_MS = 60_000L
        const val TICK_MS = 1_000L
        const val NEAR_END_MS = 3_000L
    }
}
