package dev.fuzz.feature.player

import dev.fuzz.core.domain.lyrics.LyricsRepository
import dev.fuzz.core.domain.lyrics.LyricsResult
import dev.fuzz.core.domain.plugin.Lyrics
import dev.fuzz.core.domain.plugin.LyricsLine
import dev.fuzz.core.model.Track
import dev.fuzz.core.testing.FakePlayerController
import dev.fuzz.core.testing.SampleTracks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsViewModelTest {
    private val player = FakePlayerController()
    private val lines = listOf(LyricsLine(1_000, "Раз"), LyricsLine(5_000, "Два"), LyricsLine(9_000, "Три"))

    private val repository =
        object : LyricsRepository {
            override suspend fun lyrics(track: Track): LyricsResult =
                if (track.id == SampleTracks.all[0].id) {
                    LyricsResult.Found(Lyrics(lines, synced = true), "LRCLIB")
                } else {
                    LyricsResult.NotFound
                }
        }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `подсвечивается звучащая строка, тап перематывает`() {
        val vm = LyricsViewModel(player, repository)
        player.playQueue(SampleTracks.all)
        assertEquals("LRCLIB", (vm.state.value.content as LyricsContent.Text).source)
        assertEquals(-1, vm.state.value.currentLine)

        player.seekTo(6_000)
        assertEquals(1, vm.state.value.currentLine)

        vm.onIntent(LyricsIntent.SeekToLine(2))
        assertEquals(9_000, player.state.value.positionMs)
        assertEquals(2, vm.state.value.currentLine)
    }

    @Test
    fun `смена трека загружает его текст`() {
        val vm = LyricsViewModel(player, repository)
        player.playQueue(SampleTracks.all)
        player.skipToNext()
        assertEquals(LyricsContent.NotFound, vm.state.value.content)
        assertEquals(SampleTracks.all[1].title, vm.state.value.title)
    }
}
