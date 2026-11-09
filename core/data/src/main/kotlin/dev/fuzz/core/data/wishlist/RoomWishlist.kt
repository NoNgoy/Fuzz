package dev.fuzz.core.data.wishlist

import dev.fuzz.core.data.online.SavedOnlineTracks
import dev.fuzz.core.data.search.OnlineRanking
import dev.fuzz.core.data.sync.Clock
import dev.fuzz.core.database.dao.PlaylistDao
import dev.fuzz.core.database.dao.WishDao
import dev.fuzz.core.database.entity.WishEntity
import dev.fuzz.core.domain.search.OnlineOrigin
import dev.fuzz.core.domain.search.OnlineSearch
import dev.fuzz.core.domain.wishlist.Wish
import dev.fuzz.core.domain.wishlist.WishReason
import dev.fuzz.core.domain.wishlist.WishRequest
import dev.fuzz.core.domain.wishlist.WishlistRepository
import dev.fuzz.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

internal class RoomWishlist
    @Inject
    constructor(
        private val dao: WishDao,
        private val playlists: PlaylistDao,
        private val resolver: WishlistResolver,
        private val online: OnlineSearch,
        private val saved: SavedOnlineTracks,
        private val clock: Clock,
    ) : WishlistRepository {
        override fun observe(): Flow<List<Wish>> =
            combine(dao.observeAll(), playlists.observeAll()) { rows, lists ->
                val names = lists.associate { it.id to it.name }
                rows
                    .groupBy { WishlistResolver.key(it.artist, it.title) }
                    .values
                    .map { group ->
                        val first = group.first()
                        Wish(
                            ids = group.map { it.id },
                            title = first.title,
                            artist = first.artist,
                            album = first.album,
                            reason = reasonOf(first.reason),
                            playlists = group.mapNotNull { row -> row.playlistId?.let(names::get) }.distinct(),
                        )
                    }
            }

        override fun observeCount(): Flow<Int> = dao.observeCount()

        override suspend fun add(requests: List<WishRequest>) {
            if (requests.isEmpty()) return
            val now = clock.nowMs()
            dao.insert(
                requests.map {
                    WishEntity(
                        title = it.title,
                        artist = it.artist,
                        album = it.album,
                        durationMs = it.durationMs,
                        reason = it.reason.name.lowercase(),
                        playlistId = it.playlistId?.value,
                        position = it.position,
                        addedAt = now,
                    )
                },
            )
            resolver.resolve()
        }

        override suspend fun remove(wish: Wish) = dao.delete(wish.ids)

        override suspend fun findOnline(wish: Wish): List<Track> {
            val artist = OnlineRanking.normalize(wish.artist)
            val hits = online.search(wish.query, FULL_ORIGINS, page = 0).map { it.track }
            val (same, other) =
                hits.partition { OnlineRanking.normalize(it.artistName).contains(artist) }
            return (same + other).take(MAX_OPTIONS)
        }

        override suspend fun take(
            wish: Wish,
            track: Track,
        ) {
            saved.persist(listOf(track.id))
            val rows = dao.all().filter { it.id in wish.ids }
            resolver.fulfil(rows, track.id.value)
        }

        override suspend fun resolve(): Int = resolver.resolve()

        private fun reasonOf(value: String): WishReason =
            WishReason.entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: WishReason.Import

        private companion object {
            const val MAX_OPTIONS = 6
            val FULL_ORIGINS = setOf(OnlineOrigin.Server, OnlineOrigin.OpenCatalog)
        }
    }
