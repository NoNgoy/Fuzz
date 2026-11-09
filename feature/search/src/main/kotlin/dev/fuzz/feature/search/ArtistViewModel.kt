package dev.fuzz.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fuzz.core.domain.artist.AlbumProfile
import dev.fuzz.core.domain.artist.ArtistPages
import dev.fuzz.core.domain.artist.ArtistProfile
import dev.fuzz.core.domain.library.LibraryRepository
import dev.fuzz.core.domain.player.PlayerController
import dev.fuzz.core.domain.search.OnlineSearch
import dev.fuzz.core.model.Track
import dev.fuzz.core.ui.mvi.MviViewModel
import dev.fuzz.core.ui.mvi.UiIntent
import dev.fuzz.core.ui.mvi.UiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistState(
    val artist: ArtistProfile,
    val isLoading: Boolean = true,
    val popular: ImmutableList<Track> = persistentListOf(),
    val albums: ImmutableList<AlbumProfile> = persistentListOf(),
    val related: ImmutableList<ArtistProfile> = persistentListOf(),
    /** Треки артиста из подключений и каталогов: полные там, где они есть. */
    val tracks: ImmutableList<Track> = persistentListOf(),
    val tracksHasMore: Boolean = true,
    val tracksLoading: Boolean = false,
    val inCollection: ImmutableList<Track> = persistentListOf(),
    /** Ищем полные версии перед запуском. */
    val preparing: Boolean = false,
) : UiState

sealed interface ArtistIntent : UiIntent {
    /** Популярное, начиная с [index]; -1 — всё вперемешку. */
    data class PlayPopular(
        val index: Int,
    ) : ArtistIntent

    data class PlayTrack(
        val index: Int,
    ) : ArtistIntent

    data class PlayCollection(
        val index: Int,
    ) : ArtistIntent

    data object Shuffle : ArtistIntent

    data object LoadMoreTracks : ArtistIntent
}

@HiltViewModel
class ArtistViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val pages: ArtistPages,
        private val online: OnlineSearch,
        library: LibraryRepository,
        private val player: PlayerController,
    ) : MviViewModel<ArtistState, ArtistIntent, PlaybackEffect>(
            ArtistState(savedState.toRoute<ArtistRoute>().toProfile()),
        ) {
        private var page = 0
        private var tracksJob: Job? = null

        init {
            val name = currentState.artist.name
            viewModelScope.launch {
                val found = pages.page(currentState.artist)
                setState {
                    copy(
                        artist = found.artist.copy(pictureUrl = found.artist.pictureUrl ?: artist.pictureUrl),
                        isLoading = false,
                        popular = found.popular.toImmutableList(),
                        albums = found.albums.toImmutableList(),
                        related = found.related.toImmutableList(),
                    )
                }
            }
            library
                .observeTracks()
                .map { all -> all.filter { it.artistName.equals(name, ignoreCase = true) } }
                .onEach { mine -> setState { copy(inCollection = mine.toImmutableList()) } }
                .launchIn(viewModelScope)
            loadMoreTracks()
        }

        override fun onIntent(intent: ArtistIntent) {
            when (intent) {
                is ArtistIntent.PlayPopular -> play(currentState.popular, intent.index)
                is ArtistIntent.PlayTrack -> play(currentState.tracks, intent.index)
                is ArtistIntent.PlayCollection -> player.playQueue(currentState.inCollection, intent.index)
                ArtistIntent.Shuffle -> shuffle()
                ArtistIntent.LoadMoreTracks -> loadMoreTracks()
            }
        }

        private fun shuffle() {
            val all = (currentState.inCollection + currentState.tracks + currentState.popular).distinctBy { it.id }
            if (all.isNotEmpty()) play(all.shuffled(), 0)
        }

        private fun play(
            tracks: List<Track>,
            index: Int,
        ) {
            if (tracks.isEmpty() || currentState.preparing) return
            setState { copy(preparing = true) }
            viewModelScope.launch {
                val queue = preparePlayback(online, tracks, index)
                setState { copy(preparing = false) }
                if (queue.isEmpty()) {
                    sendEffect(PlaybackEffect.NotPlayable)
                } else {
                    player.setShuffle(false)
                    player.playQueue(queue)
                }
            }
        }

        private fun loadMoreTracks() {
            if (!currentState.tracksHasMore || tracksJob?.isActive == true) return
            setState { copy(tracksLoading = true) }
            tracksJob =
                viewModelScope.launch {
                    val next = pages.tracks(currentState.artist.name, page)
                    page++
                    setState {
                        val known = tracks.mapTo(HashSet()) { it.id }
                        val fresh = next.tracks.filter { known.add(it.id) }
                        copy(
                            tracks = (tracks + fresh).toImmutableList(),
                            tracksLoading = false,
                            // Страница без нового (источник повторяет первую) — дальше не просим.
                            tracksHasMore = next.hasMore && (fresh.isNotEmpty() || next.tracks.isEmpty()),
                        )
                    }
                }
        }
    }

/** Сначала полные версии из подключений, затем ссылки на звук; лишнее отсекаем, чтобы не ждать долго. */
internal suspend fun preparePlayback(
    online: OnlineSearch,
    tracks: List<Track>,
    index: Int,
): List<Track> {
    val slice = tracks.drop(index.coerceAtLeast(0)).take(MAX_PREPARED)
    return online.playable(online.fullVersions(slice))
}

private const val MAX_PREPARED = 25
