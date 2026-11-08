package dev.fuzz.feature.importer

import app.cash.turbine.test
import dev.fuzz.core.domain.importer.ImportItem
import dev.fuzz.core.domain.importer.ImportPlaylistUseCase
import dev.fuzz.core.domain.importer.PlaylistMatcher
import dev.fuzz.core.domain.importer.SaveImportedPlaylistUseCase
import dev.fuzz.core.domain.library.LibraryRepository
import dev.fuzz.core.domain.playlist.PlaylistRepository
import dev.fuzz.core.domain.plugin.PlaylistImporter
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.core.model.Playlist
import dev.fuzz.core.model.PlaylistId
import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId
import dev.fuzz.core.testing.SampleTracks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InputStream

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {
    private val library = SampleTracks.all
    private val playlists = FakePlaylists()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(refs: List<TrackRef>) =
        ImportViewModel(
            ImportPlaylistUseCase(setOf(FakeImporter(refs)), FakeLibrary(library), FakeMatcher()),
            SaveImportedPlaylistUseCase(playlists),
        )

    private fun ImportViewModel.pick(name: String = "Дорога домой.m3u") =
        onIntent(ImportIntent.FilePicked(name) { "".byteInputStream() })

    private fun report(vm: ImportViewModel) = vm.state.value.stage as ImportStage.Report

    @Test
    fun `report opens on doubtful tab when something needs a decision`() {
        val vm = viewModel(listOf(ref(library[0].title), ref("~" + library[1].title), ref("Нет такого")))

        vm.pick()

        val report = report(vm)
        assertEquals("Дорога домой", report.name)
        assertEquals(ReportTab.Doubtful, report.tab)
        assertEquals(1, report.count(ReportTab.Found))
        assertEquals(1, report.count(ReportTab.Doubtful))
        assertEquals(1, report.count(ReportTab.NotFound))
        assertEquals(1, report.nextPending)
    }

    @Test
    fun `confirming and rejecting move rows between tabs`() {
        val vm = viewModel(listOf(ref("~" + library[0].title), ref("~" + library[1].title)))
        vm.pick()

        vm.onIntent(ImportIntent.Confirm(0, library[0]))
        vm.onIntent(ImportIntent.Reject(1))

        assertEquals(1, report(vm).count(ReportTab.Found))
        assertEquals(1, report(vm).count(ReportTab.NotFound))
        assertEquals(-1, report(vm).nextPending)

        vm.onIntent(ImportIntent.Restore(1))
        assertEquals(1, report(vm).count(ReportTab.Doubtful))
    }

    @Test
    fun `unsupported extension is reported with the list of formats`() {
        val vm = viewModel(emptyList())

        vm.pick("list.pdf")

        assertEquals(ImportStage.PickFile, vm.state.value.stage)
        assertEquals(ImportProblem.Unsupported("pdf", "M3U"), vm.state.value.problem)
    }

    @Test
    fun `empty file is reported`() {
        val vm = viewModel(emptyList())

        vm.pick()

        assertEquals(ImportProblem.Empty, vm.state.value.problem)
    }

    @Test
    fun `save creates playlist with found and confirmed tracks only`() =
        runTest {
            val vm = viewModel(listOf(ref(library[0].title), ref("~" + library[1].title), ref("Нет такого")))
            vm.pick()
            vm.onIntent(ImportIntent.Confirm(1, library[1]))
            vm.onIntent(ImportIntent.Rename("Новое имя"))

            vm.effects.test {
                vm.onIntent(ImportIntent.Save)
                assertEquals(ImportEffect.Saved(PlaylistId("p1"), "Новое имя", 2), awaitItem())
            }
            assertEquals("Новое имя", playlists.created.single())
            assertEquals(listOf(library[0].id, library[1].id), playlists.added)
            assertTrue(report(vm).saving)
        }

    private fun ref(title: String) = TrackRef(title = title, artist = "")

    /** «~» в начале названия делает строку сомнительной, неизвестное название — ненайденной. */
    private inner class FakeMatcher : PlaylistMatcher {
        override fun match(
            refs: List<TrackRef>,
            library: List<Track>,
        ): List<ImportItem> =
            refs.map { ref ->
                val exact = library.firstOrNull { it.title == ref.title }
                val fuzzy = library.firstOrNull { "~" + it.title == ref.title }
                when {
                    exact != null -> ImportItem.Found(ref, exact)
                    fuzzy != null -> ImportItem.Doubtful(ref, listOf(fuzzy))
                    else -> ImportItem.NotFound(ref)
                }
            }
    }

    private class FakeImporter(
        private val refs: List<TrackRef>,
    ) : PlaylistImporter {
        override val id = "m3u"
        override val displayName = "M3U"
        override val formats = setOf("m3u")

        override suspend fun import(input: InputStream) = PluginResult.Ok(refs)
    }

    private class FakeLibrary(
        private val tracks: List<Track>,
    ) : LibraryRepository {
        override fun observeTracks(): Flow<List<Track>> = flowOf(tracks)

        override suspend fun getTracks(ids: List<TrackId>) = tracks.filter { it.id in ids }
    }

    private class FakePlaylists : PlaylistRepository {
        val created = mutableListOf<String>()
        val added = mutableListOf<TrackId>()

        override fun observePlaylists(): Flow<List<Playlist>> = emptyFlow()

        override fun observePlaylist(id: PlaylistId): Flow<Playlist?> = emptyFlow()

        override fun observeTracks(id: PlaylistId): Flow<List<Track>> = emptyFlow()

        override suspend fun create(name: String): PlaylistId {
            created += name
            return PlaylistId("p1")
        }

        override suspend fun rename(
            id: PlaylistId,
            name: String,
        ) = Unit

        override suspend fun delete(id: PlaylistId) = Unit

        override suspend fun addTracks(
            id: PlaylistId,
            tracks: List<TrackId>,
        ): Int {
            added += tracks
            return tracks.size
        }

        override suspend fun removeTrack(
            id: PlaylistId,
            track: TrackId,
        ) = Unit

        override suspend fun moveTrack(
            id: PlaylistId,
            from: Int,
            to: Int,
        ) = Unit
    }
}
