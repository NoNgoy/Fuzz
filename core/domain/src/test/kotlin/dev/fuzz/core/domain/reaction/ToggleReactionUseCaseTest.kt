package dev.fuzz.core.domain.reaction

import dev.fuzz.core.model.Reaction
import dev.fuzz.core.model.TrackId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ToggleReactionUseCaseTest {
    private class FakeRepository : ReactionRepository {
        val state = MutableStateFlow<Map<TrackId, Reaction>>(emptyMap())

        override fun observe(trackId: TrackId): Flow<Reaction> = state.map { it[trackId] ?: Reaction.None }

        override fun observeLiked(): Flow<Set<TrackId>> =
            state.map { map -> map.filterValues { it == Reaction.Like }.keys }

        override fun observeLikedOrdered(): Flow<List<TrackId>> = observeLiked().map { it.toList() }

        override suspend fun set(
            trackId: TrackId,
            reaction: Reaction,
        ) {
            state.value = state.value + (trackId to reaction)
        }
    }

    private val id = TrackId("t1")

    @Test
    fun `like then like again clears`() =
        runTest {
            val repo = FakeRepository()
            val toggle = ToggleReactionUseCase(repo)
            assertEquals(Reaction.Like, toggle(id, Reaction.Like))
            assertEquals(Reaction.None, toggle(id, Reaction.Like))
        }

    @Test
    fun `dislike replaces like`() =
        runTest {
            val repo = FakeRepository()
            val toggle = ToggleReactionUseCase(repo)
            toggle(id, Reaction.Like)
            assertEquals(Reaction.Dislike, toggle(id, Reaction.Dislike))
            assertEquals(Reaction.Dislike, repo.state.value[id])
        }
}
