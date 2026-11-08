package dev.fuzz.core.domain.player

import dev.fuzz.core.domain.library.LibraryRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Перемешанная коллекция. До движка волны (этап 2) карточка «Моя волна» запускает именно её,
 * чтобы кнопка на главной уже играла музыку.
 */
class PlayShuffledLibraryUseCase
    @Inject
    constructor(
        private val library: LibraryRepository,
        private val player: PlayerController,
    ) {
        /** Возвращает false, если коллекция пуста. */
        suspend operator fun invoke(): Boolean {
            val tracks = library.observeTracks().first()
            if (tracks.isEmpty()) return false
            player.setShuffle(false)
            player.playQueue(tracks.shuffled())
            return true
        }
    }
