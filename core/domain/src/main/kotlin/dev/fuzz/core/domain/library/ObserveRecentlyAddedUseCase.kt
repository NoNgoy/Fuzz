package dev.fuzz.core.domain.library

import dev.fuzz.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** «Новое в коллекции»: последние добавленные треки. */
class ObserveRecentlyAddedUseCase
    @Inject
    constructor(
        private val repository: LibraryRepository,
    ) {
        operator fun invoke(limit: Int = DEFAULT_LIMIT): Flow<List<Track>> =
            repository.observeTracks().map { tracks -> tracks.sortedByDescending { it.addedAtMs }.take(limit) }

        private companion object {
            const val DEFAULT_LIMIT = 4
        }
    }
