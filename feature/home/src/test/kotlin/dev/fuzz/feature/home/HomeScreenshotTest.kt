package dev.fuzz.feature.home

import dev.fuzz.core.testing.SampleTracks
import dev.fuzz.core.testing.captureThemedScreens
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h873dp-440dpi")
class HomeScreenshotTest {
    private val sunday = LocalDateTime.of(2026, 11, 8, 19, 30)

    @Test
    fun home() =
        captureThemedScreens("home") {
            HomeScreen(
                state = HomeState(isLoading = false, recentlyAdded = SampleTracks.all.toImmutableList()),
                onIntent = {},
                onOpenWaveSettings = {},
                onAddMusic = {},
                now = sunday,
            )
        }

    @Test
    fun homeEmpty() =
        captureThemedScreens("home_empty") {
            HomeScreen(
                state = HomeState(isLoading = false, libraryEmpty = true),
                onIntent = {},
                onOpenWaveSettings = {},
                onAddMusic = {},
                now = sunday,
            )
        }
}
