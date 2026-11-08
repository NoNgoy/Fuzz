package dev.fuzz.feature.library

import dev.fuzz.core.testing.SampleTracks
import dev.fuzz.core.testing.captureThemedScreens
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h873dp-440dpi")
class LibraryScreenshotTest {
    @Test
    fun library() =
        captureThemedScreens("library") {
            LibraryScreen(
                state =
                    LibraryState(
                        isLoading = false,
                        tracks = SampleTracks.all.toImmutableList(),
                        currentTrackId = SampleTracks.all[1].id,
                        likedCount = 128,
                    ),
                onIntent = {},
            )
        }
}
