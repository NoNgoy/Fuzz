package dev.fuzz.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fuzz.core.domain.library.CollectionEditor
import dev.fuzz.core.domain.library.ObserveLibraryTracksUseCase
import dev.fuzz.core.domain.player.PlayerController
import dev.fuzz.core.domain.playlist.PlaylistRepository
import dev.fuzz.core.domain.reaction.ReactionRepository
import dev.fuzz.core.model.PlaylistId
import dev.fuzz.core.model.Reaction
import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId
import dev.fuzz.core.ui.mvi.MviViewModel
import dev.fuzz.core.ui.mvi.UiEffect
import dev.fuzz.core.ui.mvi.UiIntent
import dev.fuzz.core.ui.mvi.UiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Что показывает экран списка: «Мне нравится», все треки на телефоне или плейлист. */
sealed interface TrackListKind {
    data object Liked : TrackListKind

    data object Downloaded : TrackListKind

    data class Playlist(
        val id: PlaylistId,
    ) : TrackListKind
}

data class TrackListState(
    val kind: TrackListKind,
    /** null, пока не загружено; для «Мне нравится» название берётся из ресурсов. */
    val title: String? = null,
    val isLoading: Boolean = true,
    val tracks: ImmutableList<Track> = persistentListOf(),
    val currentTrackId: TrackId? = null,
) : UiState

sealed interface TrackListIntent : UiIntent {
    data class Play(
        val index: Int,
    ) : TrackListIntent

    data object Shuffle : TrackListIntent

    data class Remove(
        val track: TrackId,
    ) : TrackListIntent

    data class Rename(
        val name: String,
    ) : TrackListIntent

    data object Delete : TrackListIntent

    /** Файл уже удалён с телефона системным запросом. */
    data class DeletedFromDevice(
        val track: TrackId,
    ) : TrackListIntent
}

sealed interface TrackListEffect : UiEffect {
    data object Closed : TrackListEffect
}

@HiltViewModel
class TrackListViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        observeLibrary: ObserveLibraryTracksUseCase,
        private val reactions: ReactionRepository,
        private val playlists: PlaylistRepository,
        private val player: PlayerController,
        private val collection: CollectionEditor,
    ) : MviViewModel<TrackListState, TrackListIntent, TrackListEffect>(TrackListState(kindOf(savedState))) {
        init {
            val kind = currentState.kind
            val tracks: Flow<List<Track>> =
                when (kind) {
                    TrackListKind.Liked -> {
                        combine(observeLibrary(), reactions.observeLikedOrdered()) { all, liked ->
                            val byId = all.associateBy { it.id }
                            liked.mapNotNull { byId[it] }
                        }
                    }

                    TrackListKind.Downloaded -> {
                        observeLibrary()
                    }

                    is TrackListKind.Playlist -> {
                        playlists.observeTracks(kind.id)
                    }
                }
            combine(tracks, player.state.map { it.currentTrack?.id }.distinctUntilChanged()) { list, current ->
                setState { copy(isLoading = false, tracks = list.toImmutableList(), currentTrackId = current) }
            }.launchIn(viewModelScope)
            if (kind is TrackListKind.Playlist) {
                playlists
                    .observePlaylist(kind.id)
                    .onEach { playlist ->
                        if (playlist ==
                            null
                        ) {
                            sendEffect(TrackListEffect.Closed)
                        } else {
                            setState { copy(title = playlist.name) }
                        }
                    }.launchIn(viewModelScope)
            }
        }

        override fun onIntent(intent: TrackListIntent) {
            val tracks = currentState.tracks
            when (intent) {
                is TrackListIntent.Play -> {
                    player.setShuffle(false)
                    player.playQueue(tracks, intent.index)
                }

                TrackListIntent.Shuffle -> {
                    if (tracks.isNotEmpty()) player.playQueue(tracks.shuffled(), 0)
                }

                is TrackListIntent.Remove -> {
                    viewModelScope.launch {
                        when (val kind = currentState.kind) {
                            TrackListKind.Liked -> reactions.set(intent.track, Reaction.None)
                            TrackListKind.Downloaded -> collection.hide(intent.track)
                            is TrackListKind.Playlist -> playlists.removeTrack(kind.id, intent.track)
                        }
                    }
                }

                is TrackListIntent.Rename -> {
                    playlistId()?.let { id -> viewModelScope.launch { playlists.rename(id, intent.name) } }
                }

                TrackListIntent.Delete -> {
                    playlistId()?.let { id -> viewModelScope.launch { playlists.delete(id) } }
                }

                is TrackListIntent.DeletedFromDevice -> {
                    viewModelScope.launch { collection.forgetDeleted(intent.track) }
                }
            }
        }

        private fun playlistId(): PlaylistId? = (currentState.kind as? TrackListKind.Playlist)?.id

        private companion object {
            fun kindOf(savedState: SavedStateHandle): TrackListKind {
                val route = savedState.toRoute<TrackListRoute>()
                return when {
                    route.playlistId != null -> TrackListKind.Playlist(PlaylistId(route.playlistId))
                    route.downloaded -> TrackListKind.Downloaded
                    else -> TrackListKind.Liked
                }
            }
        }
    }
