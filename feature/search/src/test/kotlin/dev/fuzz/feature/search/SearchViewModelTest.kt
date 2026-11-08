package dev.fuzz.feature.search

import dev.fuzz.core.domain.library.LibraryRepository
import dev.fuzz.core.domain.search.OnlineHit
import dev.fuzz.core.domain.search.OnlineOrigin
import dev.fuzz.core.domain.search.OnlineSearch
import dev.fuzz.core.domain.search.SearchLibraryUseCase
import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId
import dev.fuzz.core.testing.FakePlayerController
import dev.fuzz.core.testing.SampleTracks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val player = FakePlayerController()
    private val serverTrack =
        Track(TrackId("s1"), "Toxicity", "System of a Down", null, 0, playbackUri = "", format = "Дом")
    private val online = FakeOnline()

    private val library =
        object : LibraryRepository {
            override fun observeTracks() = flowOf(SampleTracks.all)

            override suspend fun getTracks(ids: List<TrackId>) = SampleTracks.all.filter { it.id in ids }
        }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `онлайн-находки показываются отдельно и играют со ссылкой`() =
        runTest(dispatcher) {
            val vm = SearchViewModel(SearchLibraryUseCase(library), online, player)
            vm.onIntent(SearchIntent.QueryChanged("toxicity"))
            advanceUntilIdle()

            assertEquals(listOf(serverTrack), vm.state.value.online)
            assertEquals(OnlineOrigin.entries.toSet(), online.lastOrigins)

            vm.onIntent(SearchIntent.PlayOnline(0))
            advanceUntilIdle()
            assertEquals("https://server/s1", player.state.value.queue.single().playbackUri)
        }

    @Test
    fun `фильтр «Коллекция» не ходит в сеть, «Мои серверы» не ищет в коллекции`() =
        runTest(dispatcher) {
            val vm = SearchViewModel(SearchLibraryUseCase(library), online, player)
            vm.onIntent(SearchIntent.SourceSelected(SearchSource.Collection))
            vm.onIntent(SearchIntent.QueryChanged("deftones"))
            advanceUntilIdle()
            assertTrue(vm.state.value.online.isEmpty())
            assertEquals(1, vm.state.value.tracks.size)
            assertEquals(0, online.calls)

            vm.onIntent(SearchIntent.SourceSelected(SearchSource.Servers))
            advanceUntilIdle()
            assertTrue(vm.state.value.tracks.isEmpty())
            assertEquals(setOf(OnlineOrigin.Server), online.lastOrigins)
        }

    private inner class FakeOnline : OnlineSearch {
        var calls = 0
        var lastOrigins: Set<OnlineOrigin> = emptySet()

        override suspend fun search(
            query: String,
            origins: Set<OnlineOrigin>,
        ): List<OnlineHit> {
            calls++
            lastOrigins = origins
            return listOf(OnlineHit(serverTrack, OnlineOrigin.Server))
        }

        override suspend fun playable(tracks: List<Track>) =
            tracks.map {
                it.copy(playbackUri = "https://server/${it.id.value}")
            }
    }
}
