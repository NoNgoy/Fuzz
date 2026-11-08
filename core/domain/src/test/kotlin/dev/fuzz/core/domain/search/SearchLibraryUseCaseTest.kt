package dev.fuzz.core.domain.search

import dev.fuzz.core.domain.library.LibraryRepository
import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchLibraryUseCaseTest {
    private fun track(
        title: String,
        artist: String,
    ) = Track(TrackId(title), title, artist, null, 1000, "file://$title")

    private val tracks =
        listOf(
            track("Chop Suey!", "System of a Down"),
            track("Toxicity", "System of a Down"),
            track("Группа крови", "Кино"),
            track("Teardrop", "Massive Attack"),
        )

    private val search =
        SearchLibraryUseCase(
            object : LibraryRepository {
                override fun observeTracks(): Flow<List<Track>> = flowOf(tracks)

                override suspend fun getTracks(ids: List<TrackId>) = tracks.filter { it.id in ids }
            },
        )

    @Test
    fun `transliterated query finds latin artist`() =
        runTest {
            val result = search.once("систем оф даун")
            assertEquals(listOf(ArtistHit("System of a Down", 2)), result.artists)
            assertEquals(2, result.tracks.size)
        }

    @Test
    fun `latin query finds cyrillic title`() =
        runTest {
            val result = search.once("gruppa krovi")
            assertEquals("Группа крови", result.tracks.single().title)
        }

    @Test
    fun `blank query returns nothing`() =
        runTest {
            assertTrue(search.once("  ").isEmpty)
        }
}
