package dev.fuzz.feature.search

import dev.fuzz.core.domain.search.ArtistHit
import dev.fuzz.core.testing.SampleTracks
import dev.fuzz.core.testing.captureThemedScreens
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h873dp-440dpi")
class SearchScreenshotTest {
    @Test
    fun browse() = captureThemedScreens("search_browse") { SearchScreen(state = SearchState(), onIntent = {}) }

    @Test
    fun results() =
        captureThemedScreens("search_results") {
            SearchScreen(
                state =
                    SearchState(
                        query = "дефтонс",
                        artists = persistentListOf(ArtistHit("Deftones", trackCount = 42)),
                        tracks = SampleTracks.all.take(3).toImmutableList(),
                        searched = true,
                        online =
                            SampleTracks.all
                                .drop(3)
                                .take(2)
                                .map { it.copy(format = "Navidrome дома") }
                                .toImmutableList(),
                    ),
                onIntent = {},
            )
        }
}
