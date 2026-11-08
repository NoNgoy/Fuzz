package dev.fuzz.core.domain.library

import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId
import kotlinx.coroutines.flow.Flow

/** Чтение библиотеки. Данные приходят только из локальной базы (offline-first). */
interface LibraryRepository {
    fun observeTracks(): Flow<List<Track>>

    suspend fun getTracks(ids: List<TrackId>): List<Track>
}
