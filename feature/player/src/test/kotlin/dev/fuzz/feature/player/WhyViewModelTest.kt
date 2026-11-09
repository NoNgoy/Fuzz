package dev.fuzz.feature.player

import dev.fuzz.core.domain.wave.WaveAnchor
import dev.fuzz.core.domain.wave.WaveFactor
import dev.fuzz.core.domain.wave.WavePlayer
import dev.fuzz.core.domain.wave.WaveReason
import dev.fuzz.core.domain.wave.WaveShare
import dev.fuzz.core.domain.wave.WaveSource
import dev.fuzz.core.domain.wave.WaveStartResult
import dev.fuzz.core.domain.wave.WaveStatus
import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId
import dev.fuzz.core.model.wave.WaveSeed
import dev.fuzz.core.testing.FakePlayerController
import dev.fuzz.core.testing.SampleTracks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WhyViewModelTest {
    private val player = FakePlayerController()
    private val explained =
        WaveReason(WaveSource.Similar, WaveFactor.Tags, WaveAnchor(WaveAnchor.Kind.LikedArtist, "Deftones"))

    private val wave =
        object : WavePlayer {
            override val status: StateFlow<WaveStatus> = MutableStateFlow(WaveStatus())

            override suspend fun startWith(
                seed: WaveSeed,
                play: ((List<Track>) -> Unit)?,
            ) = WaveStartResult.Started

            override fun mix(familiarity: Int) = emptyList<WaveShare>()

            override suspend fun reason(trackId: TrackId) = explained.takeIf { trackId == SampleTracks.all[0].id }
        }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `nothing playing`() {
        assertEquals(WhyContent.Idle, WhyViewModel(player, wave).state.value.content)
    }

    @Test
    fun `wave track is explained, other track is not`() {
        val vm = WhyViewModel(player, wave)
        player.playQueue(SampleTracks.all)
        assertEquals(WhyContent.Explained(explained), vm.state.value.content)
        assertEquals(SampleTracks.all[0].title, vm.state.value.title)

        player.skipToNext()
        assertEquals(WhyContent.NotWave, vm.state.value.content)
    }
}
