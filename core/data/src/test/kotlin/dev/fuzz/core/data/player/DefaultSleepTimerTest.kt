package dev.fuzz.core.data.player

import dev.fuzz.core.data.sync.Clock
import dev.fuzz.core.domain.player.SleepTimerState
import dev.fuzz.core.model.RepeatMode
import dev.fuzz.core.testing.FakePlayerController
import dev.fuzz.core.testing.SampleTracks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSleepTimerTest {
    private val player = FakePlayerController().apply { playQueue(SampleTracks.all) }

    private fun TestScope.timer(): DefaultSleepTimer {
        val clock =
            object : Clock() {
                override fun nowMs() = testScheduler.currentTime
            }
        return DefaultSleepTimer(player, clock, backgroundScope)
    }

    @Test
    fun `countdown pauses playback when time is up`() =
        runTest(UnconfinedTestDispatcher()) {
            val timer = timer()
            timer.start(minutes = 15)
            advanceTimeBy(5 * 60_000L)
            runCurrent()
            assertEquals(SleepTimerState.Countdown(10 * 60_000L), timer.state.value)
            assertTrue(player.state.value.isPlaying)

            advanceTimeBy(10 * 60_000L)
            runCurrent()
            assertEquals(SleepTimerState.Off, timer.state.value)
            assertFalse(player.state.value.isPlaying)
        }

    @Test
    fun `cancel keeps music playing`() =
        runTest(UnconfinedTestDispatcher()) {
            val timer = timer()
            timer.start(minutes = 1)
            timer.cancel()
            advanceTimeBy(2 * 60_000L)
            assertEquals(SleepTimerState.Off, timer.state.value)
            assertTrue(player.state.value.isPlaying)
        }

    @Test
    fun `new countdown replaces the old one`() =
        runTest(UnconfinedTestDispatcher()) {
            val timer = timer()
            timer.start(minutes = 1)
            timer.start(minutes = 30)
            advanceTimeBy(2 * 60_000L)
            runCurrent()
            assertTrue(player.state.value.isPlaying)
            assertEquals(SleepTimerState.Countdown(28 * 60_000L), timer.state.value)
        }

    @Test
    fun `end of track pauses on next track and rewinds it`() =
        runTest(UnconfinedTestDispatcher()) {
            val timer = timer()
            timer.stopAfterTrack()
            player.setPosition(60_000)
            assertEquals(SleepTimerState.EndOfTrack, timer.state.value)
            assertTrue(player.state.value.isPlaying)

            player.skipToNext()
            player.setPosition(400)
            assertEquals(SleepTimerState.Off, timer.state.value)
            assertFalse(player.state.value.isPlaying)
            assertEquals(1, player.state.value.currentIndex)
        }

    @Test
    fun `end of track works with repeat one`() =
        runTest(UnconfinedTestDispatcher()) {
            player.setRepeatMode(RepeatMode.One)
            val timer = timer()
            timer.stopAfterTrack()
            player.setPosition(player.state.value.durationMs - 500)
            assertEquals(SleepTimerState.EndOfTrack, timer.state.value)

            player.setPosition(200)
            assertEquals(SleepTimerState.Off, timer.state.value)
            assertFalse(player.state.value.isPlaying)
        }

    @Test
    fun `manual pause does not end the track timer`() =
        runTest(UnconfinedTestDispatcher()) {
            val timer = timer()
            timer.stopAfterTrack()
            player.setPosition(30_000)
            player.togglePlayPause()
            assertEquals(SleepTimerState.EndOfTrack, timer.state.value)
        }
}
