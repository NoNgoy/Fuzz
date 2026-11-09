package dev.fuzz.feature.player

import dev.fuzz.core.domain.player.EqualizerBands
import dev.fuzz.core.domain.player.EqualizerPreset
import dev.fuzz.core.domain.player.SoundEffects
import dev.fuzz.core.domain.player.SoundSettings
import dev.fuzz.core.domain.player.SoundSettingsRepository
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
class SoundViewModelTest {
    private val stored = MutableStateFlow(SoundSettings())
    private val repository =
        object : SoundSettingsRepository {
            override val settings = stored

            override suspend fun update(transform: (SoundSettings) -> SoundSettings) = stored.update(transform)
        }
    private val bands = EqualizerBands(EqualizerPreset.REFERENCE_HZ, minDb = -15f, maxDb = 15f, hasBassBoost = true)
    private val effects =
        object : SoundEffects {
            override val bands = MutableStateFlow<EqualizerBands?>(this@SoundViewModelTest.bands)
        }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `preset turns equalizer on and shows its gains`() {
        val vm = SoundViewModel(repository, effects)
        vm.onIntent(SoundIntent.PickPreset(EqualizerPreset.Rock))
        assertTrue(stored.value.equalizerEnabled)
        assertEquals(EqualizerPreset.Rock.gainsDb, vm.state.value.gains)
    }

    @Test
    fun `moving a band starts custom setting from current gains`() {
        val vm = SoundViewModel(repository, effects)
        vm.onIntent(SoundIntent.PickPreset(EqualizerPreset.Bass))
        vm.onIntent(SoundIntent.SetBand(4, 3f))

        assertNull(stored.value.preset)
        assertEquals(listOf(6f, 4f, 0f, 0f, 3f), stored.value.customGainsDb)
    }

    @Test
    fun `fade is clamped`() {
        val vm = SoundViewModel(repository, effects)
        vm.onIntent(SoundIntent.SetFade(40))
        assertEquals(SoundSettings.MAX_FADE_SECONDS, stored.value.fadeSeconds)
    }
}
