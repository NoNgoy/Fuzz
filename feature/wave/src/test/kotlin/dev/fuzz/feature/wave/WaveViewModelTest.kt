package dev.fuzz.feature.wave

import dev.fuzz.core.domain.wave.WavePlayer
import dev.fuzz.core.domain.wave.WaveSettingsRepository
import dev.fuzz.core.domain.wave.WaveShare
import dev.fuzz.core.domain.wave.WaveSource
import dev.fuzz.core.domain.wave.WaveStartResult
import dev.fuzz.core.domain.wave.WaveStatus
import dev.fuzz.core.model.wave.WaveActivity
import dev.fuzz.core.model.wave.WaveCharacter
import dev.fuzz.core.model.wave.WaveSeed
import dev.fuzz.core.model.wave.WaveSettings
import dev.fuzz.core.testing.FakePlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WaveViewModelTest {
    private val settings = FakeSettings()
    private val wave = FakeWave()
    private val player = FakePlayerController()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = WaveViewModel(settings, wave, player)

    @Test
    fun `slider is saved only when released`() {
        val vm = viewModel()

        vm.onIntent(WaveIntent.DragFamiliarity(80))
        assertEquals(80, vm.state.value.familiarity)
        assertEquals(WaveSettings.DEFAULT_FAMILIARITY, settings.settings.value.familiarity)
        assertEquals(80, wave.lastMix)

        vm.onIntent(WaveIntent.CommitFamiliarity)
        assertEquals(80, settings.settings.value.familiarity)
    }

    @Test
    fun `moving the slider drops the character preset`() {
        settings.settings.value = WaveSettings(character = WaveCharacter.Popular)
        val vm = viewModel()

        vm.onIntent(WaveIntent.DragFamiliarity(10))
        vm.onIntent(WaveIntent.CommitFamiliarity)

        assertNull(settings.settings.value.character)
    }

    @Test
    fun `chips toggle on and off`() {
        val vm = viewModel()

        vm.onIntent(WaveIntent.ToggleActivity(WaveActivity.Workout))
        assertEquals(WaveActivity.Workout, vm.state.value.settings.activity)

        vm.onIntent(WaveIntent.ToggleActivity(WaveActivity.Workout))
        assertNull(vm.state.value.settings.activity)
    }

    @Test
    fun `play starts the wave, then pauses the player`() {
        val vm = viewModel()

        vm.onIntent(WaveIntent.PlayPause)
        assertEquals(1, wave.starts)
        assertTrue(vm.state.value.waveActive)

        vm.onIntent(WaveIntent.PlayPause)
        assertEquals(1, wave.starts)
        assertEquals(false, player.state.value.isPlaying)
    }

    private class FakeSettings : WaveSettingsRepository {
        override val settings = MutableStateFlow(WaveSettings())

        override suspend fun update(transform: (WaveSettings) -> WaveSettings) = settings.update(transform)
    }

    private inner class FakeWave : WavePlayer {
        override val status = MutableStateFlow(WaveStatus())
        var starts = 0
        var lastMix = -1

        override suspend fun start(seed: WaveSeed): WaveStartResult {
            starts++
            player.playQueue(emptyList())
            status.value = WaveStatus(active = true, seed = seed)
            return WaveStartResult.Started
        }

        override fun mix(familiarity: Int): List<WaveShare> {
            lastMix = familiarity
            return listOf(WaveShare(WaveSource.Favorites, 1f))
        }
    }
}
