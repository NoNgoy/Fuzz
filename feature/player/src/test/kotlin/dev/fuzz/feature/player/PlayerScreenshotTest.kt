package dev.fuzz.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fuzz.core.model.PlaybackState
import dev.fuzz.core.model.Reaction
import dev.fuzz.core.model.RepeatMode
import dev.fuzz.core.testing.SampleTracks
import dev.fuzz.core.testing.captureThemedScreens
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h873dp-440dpi")
class PlayerScreenshotTest {
    private val playing =
        PlayerUiState(
            playback =
                PlaybackState(
                    currentTrack = SampleTracks.all[0],
                    isPlaying = true,
                    positionMs = 83_000,
                    durationMs = SampleTracks.all[0].durationMs,
                    queue = SampleTracks.all,
                    currentIndex = 0,
                    repeatMode = RepeatMode.All,
                ),
            reaction = Reaction.Like,
        )

    @Test
    fun player() =
        captureThemedScreens("player") {
            PlayerContent(state = playing, onCollapse = {}, onIntent = {}, onOpenQueue = {})
        }

    @Test
    fun miniPlayer() =
        captureThemedScreens("mini_player") {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                MiniPlayerContent(state = playing, onExpand = {}, onIntent = {})
            }
        }
}
