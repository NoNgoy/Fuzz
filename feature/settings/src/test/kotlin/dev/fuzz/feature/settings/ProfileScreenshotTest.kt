package dev.fuzz.feature.settings

import dev.fuzz.core.domain.stats.RecapArtist
import dev.fuzz.core.domain.stats.RecapTrack
import dev.fuzz.core.domain.stats.YearRecap
import dev.fuzz.core.testing.captureThemedScreens
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h873dp-440dpi")
class ProfileScreenshotTest {
    @Test
    fun profile() =
        captureThemedScreens("profile") {
            ProfileScreen(onOpenImport = {}, onOpenConnections = {}, onOpenSettings = {})
        }

    @Test
    fun yearRecap() =
        captureThemedScreens("year_recap") {
            YearRecapScreen(
                YearRecap(
                    year = 2026,
                    listenedMs = 152 * 3_600_000L,
                    plays = 2_431,
                    distinctTracks = 612,
                    topTracks =
                        listOf(
                            RecapTrack("1", "Кукушка", "Кино", null, 48),
                            RecapTrack("2", "Выхода нет", "Сплин", null, 31),
                            RecapTrack("3", "Искала", "Земфира", null, 22),
                        ),
                    topArtists =
                        listOf(
                            RecapArtist("Кино", 410, 31 * 3_600_000L),
                            RecapArtist("Сплин", 260, 19 * 3_600_000L),
                            RecapArtist("Земфира", 150, 11 * 3_600_000L),
                        ),
                    topMonth = 7,
                    newArtists = 14,
                ),
                onBack = {},
                isDecember = false,
            )
        }

    @Test
    fun settings() =
        captureThemedScreens("settings") {
            SettingsScreen(SettingsState(hiddenTracks = 3), onIntent = {
            }, actions = SettingsActions(), version = "0.1.8")
        }
}
