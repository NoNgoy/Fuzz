package dev.fuzz.core.data.reaction

import dev.fuzz.core.data.online.SavedOnlineTracks
import dev.fuzz.core.database.dao.ReactionDao
import dev.fuzz.core.database.entity.ReactionEntity
import dev.fuzz.core.domain.reaction.ReactionRepository
import dev.fuzz.core.model.Reaction
import dev.fuzz.core.model.TrackId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class OfflineReactionRepository
    @Inject
    constructor(
        private val dao: ReactionDao,
        private val saved: SavedOnlineTracks,
    ) : ReactionRepository {
        override fun observe(trackId: TrackId): Flow<Reaction> =
            dao.observe(trackId.value).map { it.toReaction() }.distinctUntilChanged()

        override fun observeLiked(): Flow<Set<TrackId>> =
            dao.observeLiked().map { ids ->
                ids.mapTo(HashSet(), ::TrackId)
            }

        override fun observeLikedOrdered(): Flow<List<TrackId>> =
            dao.observeLikedOrdered().map { ids -> ids.map(::TrackId) }

        override suspend fun set(
            trackId: TrackId,
            reaction: Reaction,
        ) {
            // Лайк трека из сети сохраняет его в коллекцию, иначе он пропал бы из «Мне нравится».
            if (reaction == Reaction.Like) saved.persist(listOf(trackId))
            dao.upsert(ReactionEntity(trackId.value, reaction.toDb(), System.currentTimeMillis()))
        }
    }

private fun Int?.toReaction(): Reaction =
    when (this) {
        1 -> Reaction.Like
        -1 -> Reaction.Dislike
        else -> Reaction.None
    }

private fun Reaction.toDb(): Int =
    when (this) {
        Reaction.Like -> 1
        Reaction.Dislike -> -1
        Reaction.None -> 0
    }
