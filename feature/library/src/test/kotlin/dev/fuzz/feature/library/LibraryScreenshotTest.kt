package dev.fuzz.feature.library

import dev.fuzz.core.domain.wishlist.Wish
import dev.fuzz.core.domain.wishlist.WishReason
import dev.fuzz.core.model.Playlist
import dev.fuzz.core.model.PlaylistId
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
                        playlists =
                            persistentListOf(
                                Playlist(PlaylistId("1"), "Дорога домой", trackCount = 48, updatedAtMs = 0),
                                Playlist(PlaylistId("2"), "Спортзал", trackCount = 61, updatedAtMs = 0),
                            ),
                    ),
                onIntent = {},
            )
        }

    @Test
    fun playlist() =
        captureThemedScreens("playlist") {
            TrackListScreen(
                state =
                    TrackListState(
                        kind = TrackListKind.Playlist(PlaylistId("1")),
                        title = "Дорога домой",
                        isLoading = false,
                        tracks = SampleTracks.all.take(5).toImmutableList(),
                        currentTrackId = SampleTracks.all[2].id,
                    ),
                onIntent = {},
                onBack = {},
            )
        }

    @Test
    fun likedEmpty() =
        captureThemedScreens("liked_empty") {
            TrackListScreen(state = TrackListState(kind = TrackListKind.Liked, isLoading = false), onIntent = {
            }, onBack = {})
        }

    @Test
    fun wishlist() =
        captureThemedScreens("wishlist") {
            WishlistScreen(
                state =
                    WishlistState(
                        isLoading = false,
                        wishes =
                            persistentListOf(
                                Wish(
                                    listOf(1),
                                    "Группа крови",
                                    "Кино",
                                    null,
                                    WishReason.Import,
                                    listOf("Дорога домой"),
                                ),
                                Wish(listOf(2), "Tum Hi Ho", "Arijit Singh", null, WishReason.Preview, emptyList()),
                            ),
                    ),
                onIntent = {},
                onBack = {},
            )
        }
}
