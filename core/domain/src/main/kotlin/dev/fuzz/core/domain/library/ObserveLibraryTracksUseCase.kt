package dev.fuzz.core.domain.library

import dev.fuzz.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Все треки коллекции, отсортированные по артисту, альбому и номеру трека. */
class ObserveLibraryTracksUseCase
    @Inject
    constructor(
        private val repository: LibraryRepository,
    ) {
        operator fun invoke(): Flow<List<Track>> =
            repository.observeTracks().map { tracks ->
                tracks.sortedWith(
                    compareBy<Track>(
                        { it.artistName.lowercase() },
                        { it.albumTitle?.lowercase().orEmpty() },
                        { it.trackNumber ?: Int.MAX_VALUE },
                        { it.title.lowercase() },
                    ),
                )
            }
    }
