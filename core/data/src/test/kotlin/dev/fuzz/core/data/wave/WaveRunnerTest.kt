package dev.fuzz.core.data.wave

import dev.fuzz.core.data.sync.Clock
import dev.fuzz.core.database.dao.ListenDao
import dev.fuzz.core.database.dao.ReactionDao
import dev.fuzz.core.database.dao.TrackDao
import dev.fuzz.core.database.entity.ListenEntity
import dev.fuzz.core.database.entity.ReactionEntity
import dev.fuzz.core.database.entity.TrackEntity
import dev.fuzz.core.domain.reaction.ReactionRepository
import dev.fuzz.core.domain.wave.WaveSettingsRepository
import dev.fuzz.core.domain.wave.WaveSource
import dev.fuzz.core.domain.wave.WaveStartResult
import dev.fuzz.core.model.Reaction
import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId
import dev.fuzz.core.model.wave.WaveSettings
import dev.fuzz.core.testing.FakePlayerController
import dev.fuzz.engine.recommend.WaveEngine
import dev.fuzz.engine.recommend.model.WaveTrack
import dev.fuzz.engine.recommend.port.WaveLibrary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WaveRunnerTest {
    private val entities =
        List(COLLECTION) { i ->
            TrackEntity(
                id = "local:$i",
                title = "Трек $i",
                artistName = "Артист ${i % ARTISTS}",
                albumTitle = null,
                durationMs = 200_000,
                playbackUri = "content://$i",
                artworkUri = null,
                trackNumber = null,
                year = null,
                format = "MP3",
                addedAt = 0,
                sourceId = "local",
                updatedAt = 0,
            )
        }
    private val player = FakePlayerController()
    private val reactions = FakeReactions()
    private val listens = FakeListenDao()
    private val clock =
        object : Clock() {
            override fun nowMs() = NOW
        }

    private fun runner(collection: List<TrackEntity> = entities): WaveRunner {
        val library =
            object : WaveLibrary {
                override suspend fun tracks() =
                    collection.map { WaveTrack(TrackId(it.id), it.title, it.artistName, durationMs = it.durationMs) }
            }
        return WaveRunner(
            engine = WaveEngine(library, clock = { NOW }),
            library = library,
            catalogs = emptyList(),
            data = WaveTracks(listens, FakeReactionDao(), FakeTrackDao(collection), ExternalTrackRegistry(), clock),
            player = player,
            settings = FakeSettings(),
            recorder = ListenRecorder(player, listens, clock),
            reactionRepository = reactions,
            clock = clock,
        )
    }

    @Test
    fun `start plays a batch from the collection`() =
        runTest(UnconfinedTestDispatcher()) {
            val wave = runner().apply { attach(backgroundScope) }

            assertEquals(WaveStartResult.Started, wave.start())

            val state = player.state.value
            assertTrue(state.queue.isNotEmpty())
            assertEquals(state.queue.size, state.queue.map { it.id }.toSet().size)
            assertEquals(0, state.currentIndex)
            assertTrue(wave.status.value.active)
        }

    @Test
    fun `wave explains its tracks and only them`() =
        runTest(UnconfinedTestDispatcher()) {
            val wave = runner().apply { attach(backgroundScope) }
            val outside = TrackId("не из волны")
            assertNull(wave.reason(outside))

            wave.start()

            val current = player.state.value.currentTrack!!.id
            assertNotNull(wave.reason(current))
            assertNull(wave.reason(outside))
        }

    @Test
    fun `empty collection does not start`() =
        runTest(UnconfinedTestDispatcher()) {
            val wave = runner(emptyList()).apply { attach(backgroundScope) }

            assertEquals(WaveStartResult.EmptyLibrary, wave.start())
            assertFalse(wave.status.value.active)
            assertTrue(player.state.value.queue.isEmpty())
        }

    @Test
    fun `queue is refilled when three tracks are left`() =
        runTest(UnconfinedTestDispatcher()) {
            val wave = runner().apply { attach(backgroundScope) }
            wave.start()
            val first = player.state.value.queue.size

            player.skipToQueueItem(first - 3)

            val queue = player.state.value.queue
            assertTrue("очередь должна вырасти: было $first, стало ${queue.size}", queue.size > first)
            assertEquals(queue.size, queue.map { it.id }.toSet().size)
        }

    @Test
    fun `dislike skips the track and keeps it out of the queue`() =
        runTest(UnconfinedTestDispatcher()) {
            val wave = runner().apply { attach(backgroundScope) }
            wave.start()
            val disliked = player.state.value.currentTrack!!.id

            reactions.set(disliked, Reaction.Dislike)

            val state = player.state.value
            assertEquals(1, state.currentIndex)
            assertFalse(state.queue.drop(1).any { it.id == disliked })
        }

    @Test
    fun `listens are written to history`() =
        runTest(UnconfinedTestDispatcher()) {
            val wave = runner().apply { attach(backgroundScope) }
            ListenRecorder(player, listens, clock).start(backgroundScope)
            wave.start()
            val first = player.state.value.currentTrack!!

            player.setPosition(12_000)
            player.skipToNext()

            val listen = listens.rows.single()
            assertEquals(first.id.value, listen.trackId)
            assertEquals(12_000, listen.playedMs)
            assertFalse(listen.completed)
        }

    @Test
    fun `playing something else releases the wave`() =
        runTest(UnconfinedTestDispatcher()) {
            val wave = runner().apply { attach(backgroundScope) }
            wave.start()

            player.playQueue(listOf(Track(TrackId("other"), "Чужой", "Кто-то", null, 1, "content://other")))

            assertFalse(wave.status.value.active)
        }

    @Test
    fun `mix covers only available sources and sums to one`() {
        val mix = runner().mix(WaveSettings.MAX_FAMILIARITY)

        assertTrue(mix.none { it.source == WaveSource.Preview || it.source == WaveSource.OpenCatalog })
        assertEquals(1f, mix.sumOf { it.share.toDouble() }.toFloat(), 0.001f)
    }

    private class FakeTrackDao(
        private val all: List<TrackEntity>,
    ) : TrackDao {
        override fun observeAll(): Flow<List<TrackEntity>> = flowOf(all)

        override suspend fun getByIds(ids: List<String>) = all.filter { it.id in ids }

        override suspend fun getAnyByIds(ids: List<String>) = all.filter { it.id in ids }

        override suspend fun getBySource(sourceId: String) = all.filter { it.sourceId == sourceId }

        override suspend fun upsert(tracks: List<TrackEntity>) = Unit

        override suspend fun markDeleted(
            ids: List<String>,
            now: Long,
        ) = Unit

        override suspend fun setHidden(
            id: String,
            hidden: Boolean,
            now: Long,
        ) = Unit

        override suspend fun restoreAllHidden(now: Long) = Unit

        override fun observeHiddenCount(): Flow<Int> = flowOf(0)
    }

    private class FakeListenDao : ListenDao {
        val rows = mutableListOf<ListenEntity>()

        override suspend fun insert(entity: ListenEntity) {
            rows += entity
        }

        override suspend fun since(sinceMs: Long) = rows.filter { it.startedAt >= sinceMs }

        override suspend fun deleteOlderThan(beforeMs: Long) {
            rows.removeAll { it.startedAt < beforeMs }
        }

        override suspend fun clearAll() = rows.clear()
    }

    private class FakeReactionDao : ReactionDao {
        override fun observe(trackId: String): Flow<Int?> = flowOf(null)

        override fun observeLiked(): Flow<List<String>> = flowOf(emptyList())

        override fun observeLikedOrdered(): Flow<List<String>> = flowOf(emptyList())

        override suspend fun all(): List<ReactionEntity> = emptyList()

        override suspend fun upsert(entity: ReactionEntity) = Unit
    }

    private class FakeReactions : ReactionRepository {
        private val map = MutableStateFlow(emptyMap<TrackId, Reaction>())

        override fun observe(trackId: TrackId) = map.map { it[trackId] ?: Reaction.None }

        override fun observeLiked() = map.map { m -> m.filterValues { it == Reaction.Like }.keys }

        override fun observeLikedOrdered() = observeLiked().map { it.toList() }

        override suspend fun set(
            trackId: TrackId,
            reaction: Reaction,
        ) = map.update { it + (trackId to reaction) }
    }

    private class FakeSettings : WaveSettingsRepository {
        override val settings = MutableStateFlow(WaveSettings())

        override suspend fun update(transform: (WaveSettings) -> WaveSettings) = settings.update(transform)
    }

    private companion object {
        const val COLLECTION = 60
        const val ARTISTS = 15
        const val NOW = 1_800_000_000_000L
    }
}
