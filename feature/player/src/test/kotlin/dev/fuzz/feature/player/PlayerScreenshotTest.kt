package dev.fuzz.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fuzz.core.domain.player.SleepTimerState
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
    fun playerWithSleepTimer() =
        captureThemedScreens("player_sleep_on") {
            PlayerContent(
                state = playing.copy(sleep = SleepTimerState.Countdown(24 * 60_000L + 30_000)),
                onCollapse = {},
                onIntent = {},
                onOpenQueue = {},
            )
        }

    @Test
    fun sleepOptions() =
        captureThemedScreens("player_sleep") {
            androidx.compose.material3.Surface {
                SleepOptions(sleep = SleepTimerState.EndOfTrack, onPick = {})
            }
        }

    @Test
    fun miniPlayer() =
        captureThemedScreens("mini_player") {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                MiniPlayerContent(state = playing, onExpand = {}, onIntent = {})
            }
        }

    @Test
    fun lyrics() =
        captureThemedScreens("player_lyrics") {
            androidx.compose.material3.Surface {
                LyricsContentView(
                    state =
                        LyricsState(
                            title = "Ночной трамвай",
                            artist = "Тестовая группа",
                            content =
                                LyricsContent.Text(
                                    kotlinx.collections.immutable.persistentListOf(
                                        dev.fuzz.core.domain.plugin.LyricsLine(
                                            1_000,
                                            "Первая строка придуманного текста",
                                        ),
                                        dev.fuzz.core.domain.plugin.LyricsLine(
                                            5_000,
                                            "Вторая строка звучит прямо сейчас",
                                        ),
                                        dev.fuzz.core.domain.plugin.LyricsLine(
                                            9_000,
                                            "Третья строка ждёт своей очереди",
                                        ),
                                        dev.fuzz.core.domain.plugin.LyricsLine(13_000, ""),
                                        dev.fuzz.core.domain.plugin.LyricsLine(
                                            15_000,
                                            "Припев после проигрыша",
                                        ),
                                    ),
                                    synced = true,
                                    source = "LRCLIB",
                                ),
                            currentLine = 1,
                        ),
                    onIntent = {},
                )
            }
        }
}
