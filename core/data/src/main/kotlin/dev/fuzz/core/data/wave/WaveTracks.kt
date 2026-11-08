package dev.fuzz.core.data.wave

import dev.fuzz.core.data.library.toModel
import dev.fuzz.core.data.sync.Clock
import dev.fuzz.core.database.dao.ListenDao
import dev.fuzz.core.database.dao.ReactionDao
import dev.fuzz.core.database.dao.TrackDao
import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId
import dev.fuzz.engine.recommend.model.TrackOrigin
import dev.fuzz.engine.recommend.model.WavePick
import dev.fuzz.engine.recommend.model.WaveSignal
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** Данные волны из базы: история для профиля вкуса и превращение пачки движка в треки плеера. */
class WaveTracks
    @Inject
    internal constructor(
        private val listens: ListenDao,
        private val reactions: ReactionDao,
        private val tracks: TrackDao,
        private val external: ExternalTrackRegistry,
        private val clock: Clock,
    ) {
        /** История для профиля вкуса: прослушивания за полгода и все оценки. */
        suspend fun history(): List<WaveSignal> {
            val played =
                listens.since(clock.nowMs() - ListenRecorder.HISTORY_MS).map {
                    WaveSignal.Listen(TrackId(it.trackId), it.startedAt, it.playedMs, it.completed)
                }
            val rated =
                reactions.all().mapNotNull {
                    when (it.reaction) {
                        1 -> WaveSignal.Like(TrackId(it.trackId), it.updatedAt)
                        -1 -> WaveSignal.Dislike(TrackId(it.trackId), it.updatedAt)
                        else -> null
                    }
                }
            return (played + rated).sortedBy { it.atMs }
        }

        /** Пачка волны в треки плеера, в том же порядке. Внешние треки, для которых нет ссылки, выпадают. */
        suspend fun resolve(picks: List<WavePick>): List<Track> {
            val ids = picks.map { it.track.id }
            val local =
                ids.chunked(SQL_CHUNK).flatMap { chunk -> tracks.getByIds(chunk.map { it.value }) }
                    .associate { TrackId(it.id) to it.toModel() }
            val remote =
                coroutineScope {
                    picks.filter { it.track.origin != TrackOrigin.Collection }
                        .map { pick ->
                            async { withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { external.resolve(pick.track.id) } }
                        }
                        .awaitAll()
                        .filterNotNull()
                        .associateBy { it.id }
                }
            return ids.mapNotNull { local[it] ?: remote[it] }
        }

        private companion object {
            const val SQL_CHUNK = 500
            const val RESOLVE_TIMEOUT_MS = 4_000L
        }
    }
