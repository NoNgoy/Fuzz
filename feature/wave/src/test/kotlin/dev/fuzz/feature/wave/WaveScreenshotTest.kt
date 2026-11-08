package dev.fuzz.feature.wave

import dev.fuzz.core.domain.wave.WaveShare
import dev.fuzz.core.domain.wave.WaveSource
import dev.fuzz.core.model.wave.WaveActivity
import dev.fuzz.core.model.wave.WaveMood
import dev.fuzz.core.model.wave.WaveSettings
import dev.fuzz.core.testing.captureThemedScreens
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h873dp-440dpi")
class WaveScreenshotTest {
    @Test
    fun waveSettings() =
        captureThemedScreens("wave_settings") {
            WaveScreen(
                state =
                    WaveState(
                        settings =
                            WaveSettings(familiarity = 40, activity = WaveActivity.Commuting, mood = WaveMood.Calm),
                        mix =
                            persistentListOf(
                                WaveShare(WaveSource.Favorites, 0.42f),
                                WaveShare(WaveSource.Unheard, 0.22f),
                                WaveShare(WaveSource.Similar, 0.22f),
                                WaveShare(WaveSource.Preview, 0.14f),
                            ),
                    ),
                onIntent = {},
                onBack = {},
            )
        }
}
