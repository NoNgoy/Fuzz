package dev.fuzz.core.domain.reaction

import dev.fuzz.core.model.Reaction
import dev.fuzz.core.model.TrackId
import kotlinx.coroutines.flow.Flow

interface ReactionRepository {
    fun observe(trackId: TrackId): Flow<Reaction>

    fun observeLiked(): Flow<Set<TrackId>>

    suspend fun set(
        trackId: TrackId,
        reaction: Reaction,
    )
}
