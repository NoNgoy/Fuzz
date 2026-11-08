package dev.fuzz.core.domain.reaction

import dev.fuzz.core.model.Reaction
import dev.fuzz.core.model.TrackId
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Повторное нажатие снимает оценку, лайк и дизлайк взаимоисключают друг друга. */
class ToggleReactionUseCase
    @Inject
    constructor(
        private val repository: ReactionRepository,
    ) {
        suspend operator fun invoke(
            trackId: TrackId,
            reaction: Reaction,
        ): Reaction {
            val current = repository.observe(trackId).first()
            val next = if (current == reaction) Reaction.None else reaction
            repository.set(trackId, next)
            return next
        }
    }
