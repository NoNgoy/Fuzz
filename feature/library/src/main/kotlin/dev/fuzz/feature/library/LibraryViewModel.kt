package dev.fuzz.feature.library

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fuzz.core.domain.library.ObserveLibraryTracksUseCase
import dev.fuzz.core.domain.player.PlayerController
import dev.fuzz.core.domain.playlist.PlaylistRepository
import dev.fuzz.core.domain.reaction.ReactionRepository
import dev.fuzz.core.model.Playlist
import dev.fuzz.core.model.PlaylistId
import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId
import dev.fuzz.core.ui.mvi.MviViewModel
import dev.fuzz.core.ui.mvi.UiEffect
import dev.fuzz.core.ui.mvi.UiIntent
import dev.fuzz.core.ui.mvi.UiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryState(
    val isLoading: Boolean = true,
    val tracks: ImmutableList<Track> = persistentListOf(),
    val currentTrackId: TrackId? = null,
    val likedCount: Int = 0,
    val playlists: ImmutableList<Playlist> = persistentListOf(),
) : UiState

sealed interface LibraryIntent : UiIntent {
    data class PlayTrack(
        val index: Int,
    ) : LibraryIntent

    data object ShuffleAll : LibraryIntent

    data class CreatePlaylist(
        val name: String,
        /** Трек, из меню которого создают плейлист: сразу кладётся внутрь. */
        val withTrack: TrackId? = null,
    ) : LibraryIntent

    data class AddToPlaylist(
        val playlist: PlaylistId,
        val track: TrackId,
    ) : LibraryIntent
}

sealed interface LibraryEffect : UiEffect {
    data class AddedToPlaylist(
        val playlistName: String,
        val added: Boolean,
    ) : LibraryEffect

    data class OpenPlaylist(
        val id: PlaylistId,
    ) : LibraryEffect
}

@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        observeTracks: ObserveLibraryTracksUseCase,
        reactions: ReactionRepository,
        private val playlists: PlaylistRepository,
        private val player: PlayerController,
    ) : MviViewModel<LibraryState, LibraryIntent, LibraryEffect>(LibraryState()) {
        init {
            combine(
                observeTracks(),
                player.state.map { it.currentTrack?.id }.distinctUntilChanged(),
                reactions.observeLiked(),
                playlists.observePlaylists(),
            ) { tracks, currentId, liked, lists ->
                setState {
                    copy(
                        isLoading = false,
                        tracks = tracks.toImmutableList(),
                        currentTrackId = currentId,
                        likedCount = tracks.count { it.id in liked },
                        playlists = lists.toImmutableList(),
                    )
                }
            }.launchIn(viewModelScope)
        }

        override fun onIntent(intent: LibraryIntent) {
            val tracks = currentState.tracks
            when (intent) {
                is LibraryIntent.PlayTrack -> {
                    player.setShuffle(false)
                    player.playQueue(tracks, intent.index)
                }

                LibraryIntent.ShuffleAll -> {
                    if (tracks.isNotEmpty()) player.playQueue(tracks.shuffled(), 0)
                }

                is LibraryIntent.CreatePlaylist -> {
                    viewModelScope.launch {
                        val id = playlists.create(intent.name)
                        if (intent.withTrack == null) {
                            sendEffect(LibraryEffect.OpenPlaylist(id))
                        } else {
                            playlists.addTracks(id, listOf(intent.withTrack))
                            sendEffect(LibraryEffect.AddedToPlaylist(intent.name, added = true))
                        }
                    }
                }

                is LibraryIntent.AddToPlaylist -> {
                    viewModelScope.launch {
                        val added = playlists.addTracks(intent.playlist, listOf(intent.track)) > 0
                        val name = currentState.playlists.firstOrNull { it.id == intent.playlist }?.name.orEmpty()
                        sendEffect(LibraryEffect.AddedToPlaylist(name, added))
                    }
                }
            }
        }
    }
