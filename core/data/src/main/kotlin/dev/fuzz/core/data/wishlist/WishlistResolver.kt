package dev.fuzz.core.data.wishlist

import dev.fuzz.core.data.search.OnlineRanking
import dev.fuzz.core.data.sync.Clock
import dev.fuzz.core.database.dao.PlaylistDao
import dev.fuzz.core.database.dao.TrackDao
import dev.fuzz.core.database.dao.WishDao
import dev.fuzz.core.database.entity.PlaylistTrackEntity
import dev.fuzz.core.database.entity.WishEntity
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Исполняет желания: если трек с тем же исполнителем и названием появился в коллекции, ставит его
 * на место в плейлистах и убирает из «Хочу добавить». Превью не считается: это не полный трек.
 */
internal class WishlistResolver
    @Inject
    constructor(
        private val wishes: WishDao,
        private val tracks: TrackDao,
        private val playlists: PlaylistDao,
        private val clock: Clock,
    ) {
        suspend fun resolve(): Int {
            val pending = wishes.all()
            if (pending.isEmpty()) return 0
            val byKey =
                tracks
                    .observeAll()
                    .first()
                    .filter { it.format != PREVIEW_LABEL }
                    .associateBy { key(it.artistName, it.title) }
            val done =
                pending.filter { wish ->
                    byKey[key(wish.artist, wish.title)]?.let { fulfil(wish, it.id) } != null
                }
            if (done.isNotEmpty()) wishes.delete(done.map { it.id })
            return done.distinctBy { key(it.artist, it.title) }.size
        }

        /** Ставит [trackId] туда, где его ждало желание, и удаляет само желание. */
        suspend fun fulfil(
            group: List<WishEntity>,
            trackId: String,
        ) {
            group.forEach { fulfil(it, trackId) }
            wishes.delete(group.map { it.id })
        }

        private suspend fun fulfil(
            wish: WishEntity,
            trackId: String,
        ) {
            val playlist = wish.playlistId ?: return
            if (wishes.playlistAlive(playlist) == 0) return
            val entries = playlists.entries(playlist)
            if (entries.any { it.trackId == trackId }) return
            val at = (wish.position ?: entries.size).coerceIn(0, entries.size)
            val updated =
                entries.toMutableList().apply {
                    add(at, PlaylistTrackEntity(playlist, at, trackId, clock.nowMs()))
                }
            playlists.replaceEntries(playlist, updated, clock.nowMs())
        }

        companion object {
            const val PREVIEW_LABEL = "Превью"

            fun key(
                artist: String,
                title: String,
            ) = OnlineRanking.normalize(artist) + "|" + OnlineRanking.normalize(title)
        }
    }
