package dev.fuzz.core.data.library

import dev.fuzz.core.database.dao.TrackDao
import dev.fuzz.core.domain.library.LibraryRepository
import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Библиотека читается только из Room: источники пишут в базу, экраны и плеер читают её. */
internal class OfflineLibraryRepository
    @Inject
    constructor(
        private val trackDao: TrackDao,
    ) : LibraryRepository {
        override fun observeTracks(): Flow<List<Track>> =
            trackDao.observeAll().map { list ->
                list.map { it.toModel() }
            }

        override suspend fun getTracks(ids: List<TrackId>): List<Track> {
            if (ids.isEmpty()) return emptyList()
            val byId =
                ids.chunked(SQLITE_MAX_ARGS).flatMap { chunk ->
                    trackDao.getByIds(chunk.map { it.value })
                }.associateBy { it.id }
            // Порядок ответа совпадает с порядком запроса: это важно для восстановления очереди.
            return ids.mapNotNull { byId[it.value]?.toModel() }
        }

        private companion object {
            const val SQLITE_MAX_ARGS = 900
        }
    }
