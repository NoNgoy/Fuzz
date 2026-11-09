package dev.fuzz.feature.search

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fuzz.core.domain.player.PlayerController
import dev.fuzz.core.domain.search.ArtistHit
import dev.fuzz.core.domain.search.OnlineOrigin
import dev.fuzz.core.domain.search.OnlineSearch
import dev.fuzz.core.domain.search.SearchLibraryUseCase
import dev.fuzz.core.domain.search.SearchResults
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Фильтр источника: коллекция на телефоне, свои серверы, Jamendo, превью Deezer или всё сразу. */
enum class SearchSource(
    val collection: Boolean,
    val online: Set<OnlineOrigin>,
) {
    Everywhere(true, OnlineOrigin.entries.toSet()),
    Collection(true, emptySet()),
    Servers(false, setOf(OnlineOrigin.Server)),
    Jamendo(false, setOf(OnlineOrigin.OpenCatalog)),
    Preview(false, setOf(OnlineOrigin.Preview)),
}

data class SearchState(
    val query: String = "",
    val source: SearchSource = SearchSource.Everywhere,
    val artists: ImmutableList<ArtistHit> = persistentListOf(),
    val tracks: ImmutableList<Track> = persistentListOf(),
    val searched: Boolean = false,
    /** Найденное в сети: свои серверы, Jamendo, превью. */
    val online: ImmutableList<Track> = persistentListOf(),
    val onlineLoading: Boolean = false,
    /** Источники могут отдать ещё: при прокрутке до конца подгружается следующая страница. */
    val onlineHasMore: Boolean = false,
    val onlineLoadingMore: Boolean = false,
) : UiState {
    val showBrowse: Boolean get() = query.isBlank()
    val nothingFound: Boolean
        get() = searched && !onlineLoading && artists.isEmpty() && tracks.isEmpty() && online.isEmpty()
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

    data class PlayOnline(
        val index: Int,
    ) : SearchIntent

    data class PlayArtist(
        val name: String,
    ) : SearchIntent

    /** Список онлайн-результатов докрутили до конца. */
    data object LoadMoreOnline : SearchIntent
}

sealed interface SearchEffect : UiEffect {
    /** Ни для одного выбранного трека источник не дал ссылку на звук. */
    data object NotPlayable : SearchEffect
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val searchLibrary: SearchLibraryUseCase,
        private val onlineSearch: OnlineSearch,
        private val player: PlayerController,
    ) : MviViewModel<SearchState, SearchIntent, SearchEffect>(SearchState()) {
        private val query = MutableStateFlow("")
        private val source = MutableStateFlow(SearchSource.Everywhere)
        private var onlinePage = 0
        private var loadMoreJob: Job? = null

        init {
            combine(query.debounce(DEBOUNCE_MS), source) { q, s -> q to s }
                .flatMapLatest { (q, s) -> if (s.collection) searchLibrary(q) else flowOf(SearchResults()) }
                .onEach { result ->
                    setState {
                        copy(
                            artists = result.artists.toImmutableList(),
                            tracks = result.tracks.toImmutableList(),
                            searched = query.isNotBlank(),
                        )
                    }
                }.launchIn(viewModelScope)
            // Сеть медленнее и дороже: ждём, пока пользователь допечатает.
            combine(query.debounce(ONLINE_DEBOUNCE_MS), source) { q, s -> q to s }
                .distinctUntilChanged()
                .mapLatest { (q, s) ->
                    loadMoreJob?.cancel()
                    onlinePage = 0
                    if (q.isBlank() || s.online.isEmpty()) {
                        emptyList()
                    } else {
                        setState { copy(onlineLoading = true, online = persistentListOf()) }
                        onlineSearch.search(q, s.online, page = 0).map { it.track }
                    }
                }.onEach { found ->
                    setState {
                        copy(
                            online = found.distinctBy { it.id }.toImmutableList(),
                            onlineLoading = false,
                            onlineLoadingMore = false,
                            onlineHasMore = found.isNotEmpty(),
                        )
                    }
                }
                .launchIn(viewModelScope)
        }

        override fun onIntent(intent: SearchIntent) {
            when (intent) {
                is SearchIntent.QueryChanged -> {
                    setState { copy(query = intent.query) }
                    query.value = intent.query
                }

                is SearchIntent.SourceSelected -> {
                    setState { copy(source = intent.source) }
                    source.value = intent.source
                }

                is SearchIntent.PlayOnline -> {
                    val tracks = currentState.online.drop(intent.index)
                    viewModelScope.launch {
                        val playable = onlineSearch.playable(tracks)
                        if (playable.isNotEmpty()) player.playQueue(playable) else sendEffect(SearchEffect.NotPlayable)
                    }
                }

                is SearchIntent.PlayTrack -> {
                    player.playQueue(currentState.tracks, intent.index)
                }

                SearchIntent.LoadMoreOnline -> {
                    loadMoreOnline()
                }

                is SearchIntent.PlayArtist -> {
                    val tracks = currentState.tracks.filter { it.artistName == intent.name }
                    if (tracks.isNotEmpty()) player.playQueue(tracks.shuffled())
                }
            }
        }

        private fun loadMoreOnline() {
            val s = currentState
            if (!s.onlineHasMore || s.onlineLoading || loadMoreJob?.isActive == true) return
            val q = s.query
            val page = onlinePage + 1
            setState { copy(onlineLoadingMore = true) }
            loadMoreJob =
                viewModelScope.launch {
                    val found = onlineSearch.search(q, s.source.online, page).map { it.track }
                    if (currentState.query != q) return@launch
                    onlinePage = page
                    setState {
                        val known = online.mapTo(HashSet()) { it.id }
                        // Источник без страниц повторяет первую: если нового нет, дальше не просим.
                        val fresh = found.filter { known.add(it.id) }
                        copy(
                            online = (online + fresh).toImmutableList(),
                            onlineLoadingMore = false,
                            onlineHasMore = fresh.isNotEmpty(),
                        )
                    }
                }
        }

        private companion object {
            const val DEBOUNCE_MS = 150L
            const val ONLINE_DEBOUNCE_MS = 500L
        }
    }
