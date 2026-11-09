package dev.fuzz.feature.library

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fuzz.core.domain.wishlist.Wish
import dev.fuzz.core.domain.wishlist.WishlistRepository
import dev.fuzz.core.model.Track
import dev.fuzz.core.ui.mvi.MviViewModel
import dev.fuzz.core.ui.mvi.UiEffect
import dev.fuzz.core.ui.mvi.UiIntent
import dev.fuzz.core.ui.mvi.UiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WishlistState(
    val wishes: ImmutableList<Wish> = persistentListOf(),
    val isLoading: Boolean = true,
    /** Желание, для которого показаны варианты из подключений. */
    val picking: Wish? = null,
    val options: ImmutableList<Track> = persistentListOf(),
    val searching: Boolean = false,
) : UiState

sealed interface WishlistIntent : UiIntent {
    data class Find(
        val wish: Wish,
    ) : WishlistIntent

    data class Take(
        val track: Track,
    ) : WishlistIntent

    data object ClosePicker : WishlistIntent

    data class Remove(
        val wish: Wish,
    ) : WishlistIntent
}

sealed interface WishlistEffect : UiEffect {
    data class Taken(
        val title: String,
    ) : WishlistEffect

    data object NothingFound : WishlistEffect
}

@HiltViewModel
class WishlistViewModel
    @Inject
    constructor(
        private val wishlist: WishlistRepository,
    ) : MviViewModel<WishlistState, WishlistIntent, WishlistEffect>(WishlistState()) {
        init {
            wishlist
                .observe()
                .onEach { setState { copy(wishes = it.toImmutableList(), isLoading = false) } }
                .launchIn(viewModelScope)
            // Файл мог появиться, пока экран был закрыт.
            viewModelScope.launch { wishlist.resolve() }
        }

        override fun onIntent(intent: WishlistIntent) {
            when (intent) {
                is WishlistIntent.Find -> {
                    find(intent.wish)
                }

                is WishlistIntent.Take -> {
                    val wish = currentState.picking ?: return
                    setState { copy(picking = null, options = persistentListOf()) }
                    viewModelScope.launch {
                        wishlist.take(wish, intent.track)
                        sendEffect(WishlistEffect.Taken(intent.track.title))
                    }
                }

                WishlistIntent.ClosePicker -> {
                    setState { copy(picking = null, options = persistentListOf(), searching = false) }
                }

                is WishlistIntent.Remove -> {
                    viewModelScope.launch { wishlist.remove(intent.wish) }
                }
            }
        }

        private fun find(wish: Wish) {
            if (currentState.searching) return
            setState { copy(picking = wish, searching = true, options = persistentListOf()) }
            viewModelScope.launch {
                val options = wishlist.findOnline(wish)
                if (options.isEmpty()) {
                    setState { copy(picking = null, searching = false) }
                    sendEffect(WishlistEffect.NothingFound)
                } else {
                    setState { copy(options = options.toImmutableList(), searching = false) }
                }
            }
        }
    }
