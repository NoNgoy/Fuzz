package dev.fuzz.core.player.session

import dev.fuzz.core.domain.library.LibraryRepository
import dev.fuzz.core.domain.playlist.PlaylistRepository
import dev.fuzz.core.domain.reaction.ReactionRepository
import dev.fuzz.core.domain.search.SearchLibraryUseCase
import dev.fuzz.core.model.Playlist
import dev.fuzz.core.model.PlaylistId
import dev.fuzz.core.model.Reaction
import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowseCatalogTest {
    private val tracks =
        listOf(
            track("1", "Toxicity", "System of a Down"),
            track("2", "Aerials", "System of a Down"),
            track("3", "Утекай", "Мумий Тролль"),
            track("4", "Teardrop", "Massive Attack"),
        )
    private val mix = Playlist(PlaylistId("p1"), "В дорогу", trackCount = 2, updatedAtMs = 0)
    private val mixTracks = listOf(tracks[3], tracks[0])
    private val liked = listOf(TrackId("3"), TrackId("1"))

    private val library =
        object : LibraryRepository {
            override fun observeTracks(): Flow<List<Track>> = flowOf(tracks)

            override suspend fun getTracks(ids: List<TrackId>): List<Track> = tracks.filter { it.id in ids }
        }

    private val catalog =
        BrowseCatalog(
            BrowseTitles("Fuzz", "Моя волна", "Мне нравится", "Плейлисты", "Все треки"),
            library,
            FakePlaylists(),
            FakeReactions(),
            SearchLibraryUseCase(library),
        )

    @Test
    fun `в корне волна и три списка`() =
        runTest {
            val root = catalog.children(BrowseCatalog.ROOT)
            assertEquals(
                listOf(BrowseCatalog.WAVE, BrowseCatalog.LIKED, BrowseCatalog.PLAYLISTS, BrowseCatalog.ALL_TRACKS),
                root.map { it.id },
            )
            assertEquals(BrowseNode.Wave(BrowseCatalog.WAVE, "Моя волна"), root.first())
        }

    @Test
    fun `мне нравится в порядке лайков`() =
        runTest {
            val songs = catalog.children(BrowseCatalog.LIKED).filterIsInstance<BrowseNode.Song>()
            assertEquals(listOf("Утекай", "Toxicity"), songs.map { it.track.title })
        }

    @Test
    fun `выбор трека в плейлисте играет плейлист с этого трека`() =
        runTest {
            val folder = catalog.children(BrowseCatalog.PLAYLISTS).single() as BrowseNode.Folder
            assertEquals("В дорогу", folder.title)
            val song = catalog.children(folder.id)[1]
            assertEquals(PlayPlan.Queue(mixTracks, 1), catalog.plan(song.id))
        }

    @Test
    fun `выбор списка играет его целиком`() =
        runTest {
            assertEquals(PlayPlan.Queue(tracks, 0), catalog.plan(BrowseCatalog.ALL_TRACKS))
            assertEquals(PlayPlan.Wave, catalog.plan(BrowseCatalog.WAVE))
        }

    @Test
    fun `обычный id трека играет один трек`() =
        runTest {
            assertEquals(PlayPlan.Queue(listOf(tracks[2]), 0), catalog.plan("3"))
            assertEquals(PlayPlan.Nothing, catalog.plan("нет такого"))
        }

    @Test
    fun `голосовой запрос ищет с транслитом, пустой включает волну`() =
        runTest {
            val plan = catalog.planForQuery("систем оф э даун") as PlayPlan.Queue
            assertEquals(setOf("Toxicity", "Aerials"), plan.tracks.map { it.title }.toSet())
            assertEquals(PlayPlan.Wave, catalog.planForQuery(" "))
            assertEquals(PlayPlan.Nothing, catalog.planForQuery("zzzzqqq"))
        }

    @Test
    fun `id трека в дереве разбирается обратно`() =
        runTest {
            val id = BrowseCatalog.songId("fuzz:playlist/p1", TrackId("local:42"))
            assertEquals("fuzz:playlist/p1" to TrackId("local:42"), BrowseCatalog.parseSong(id))
            assertNull(BrowseCatalog.parseSong("local:42"))
            assertEquals("Toxicity", catalog.track(BrowseCatalog.songId(BrowseCatalog.LIKED, TrackId("1")))?.title)
        }

    private inner class FakePlaylists : PlaylistRepository {
        override fun observePlaylists(): Flow<List<Playlist>> = flowOf(listOf(mix))

        override fun observePlaylist(id: PlaylistId): Flow<Playlist?> = flowOf(mix.takeIf { it.id == id })

        override fun observeTracks(id: PlaylistId): Flow<List<Track>> =
            flowOf(
                if (id ==
                    mix.id
                ) {
                    mixTracks
                } else {
                    emptyList()
                },
            )

        override suspend fun create(name: String): PlaylistId = error("не нужно")

        override suspend fun rename(
            id: PlaylistId,
            name: String,
        ) = Unit

        override suspend fun delete(id: PlaylistId) = Unit

        override suspend fun addTracks(
            id: PlaylistId,
            tracks: List<TrackId>,
        ): Int = 0

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

    private inner class FakeReactions : ReactionRepository {
        override fun observe(trackId: TrackId): Flow<Reaction> =
            flowOf(
                if (trackId in
                    liked
                ) {
                    Reaction.Like
                } else {
                    Reaction.None
                },
            )

        override fun observeLiked(): Flow<Set<TrackId>> = flowOf(liked.toSet())

        override fun observeLikedOrdered(): Flow<List<TrackId>> = flowOf(liked)

        override suspend fun set(
            trackId: TrackId,
            reaction: Reaction,
        ) = Unit
    }

    private companion object {
        fun track(
            id: String,
            title: String,
            artist: String,
        ) = Track(TrackId(id), title, artist, albumTitle = null, durationMs = 1000, playbackUri = "content://$id")
    }
}
