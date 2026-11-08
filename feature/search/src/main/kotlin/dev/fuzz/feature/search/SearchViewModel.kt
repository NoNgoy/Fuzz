package dev.fuzz.feature.search

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fuzz.core.domain.player.PlayerController
import dev.fuzz.core.domain.search.ArtistHit
import dev.fuzz.core.domain.search.SearchLibraryUseCase
import dev.fuzz.core.model.Track
import dev.fuzz.core.ui.mvi.MviViewModel
import dev.fuzz.core.ui.mvi.UiEffect
import dev.fuzz.core.ui.mvi.UiIntent
import dev.fuzz.core.ui.mvi.UiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/** Фильтр источника. Пока работает только коллекция, онлайн-источники добавят плагины. */
enum class SearchSource { Everywhere, Collection, Jamendo, Preview }

data class SearchState(
    val query: String = "",
    val source: SearchSource = SearchSource.Everywhere,
    val artists: ImmutableList<ArtistHit> = persistentListOf(),
    val tracks: ImmutableList<Track> = persistentListOf(),
    val searched: Boolean = false,
) : UiState {
    val showBrowse: Boolean get() = query.isBlank()
}

sealed interface SearchIntent : UiIntent {
    data class QueryChanged(
        val query: String,
    ) : SearchIntent

    data class SourceSelected(
        val source: SearchSource,
    ) : SearchIntent

    data class PlayTrack(
        val index: Int,
    ) : SearchIntent

    data class PlayArtist(
        val name: String,
    ) : SearchIntent
}

sealed interface SearchEffect : UiEffect

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val searchLibrary: SearchLibraryUseCase,
        private val player: PlayerController,
    ) : MviViewModel<SearchState, SearchIntent, SearchEffect>(SearchState()) {
        private val query = MutableStateFlow("")

        init {
            query
                .debounce(DEBOUNCE_MS)
                .flatMapLatest { searchLibrary(it) }
                .onEach { result ->
                    setState {
                        copy(
                            artists = result.artists.toImmutableList(),
                            tracks = result.tracks.toImmutableList(),
                            searched = query.isNotBlank(),
                        )
                    }
                }.launchIn(viewModelScope)
        }

        override fun onIntent(intent: SearchIntent) {
            when (intent) {
                is SearchIntent.QueryChanged -> {
                    setState { copy(query = intent.query) }
                    query.value = intent.query
                }

                is SearchIntent.SourceSelected -> {
                    setState { copy(source = intent.source) }
                }

                is SearchIntent.PlayTrack -> {
                    player.playQueue(currentState.tracks, intent.index)
                }

                is SearchIntent.PlayArtist -> {
                    val tracks = currentState.tracks.filter { it.artistName == intent.name }
                    if (tracks.isNotEmpty()) player.playQueue(tracks.shuffled())
                }
            }
        }

        private companion object {
            const val DEBOUNCE_MS = 150L
        }
    }
