package dev.fuzz.core.ui.component

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fuzz.core.domain.download.DownloadStart
import dev.fuzz.core.domain.download.Downloads
import dev.fuzz.core.domain.playlist.PlaylistRepository
import dev.fuzz.core.domain.reaction.ReactionRepository
import dev.fuzz.core.domain.search.OnlineSearch
import dev.fuzz.core.model.Playlist
import dev.fuzz.core.model.PlaylistId
import dev.fuzz.core.model.Reaction
import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId
import dev.fuzz.core.ui.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Какой трек сейчас открыт в меню. Экран держит одно состояние и один [TrackMenuHost]. */
@Stable
class TrackMenuState {
    var track: Track? by mutableStateOf(null)
        private set

    fun open(track: Track) {
        this.track = track
    }

    fun close() {
        track = null
    }
}

@Composable
fun rememberTrackMenu(): TrackMenuState = remember { TrackMenuState() }

/**
 * Меню трека для любого экрана: «Мне нравится» и плейлисты. Работает и для треков из сети:
 * превью сначала меняется на полную версию из подключений, затем трек сохраняется в коллекцию.
 */
@Composable
fun TrackMenuHost(
    state: TrackMenuState,
    showLike: Boolean = true,
    onHide: ((Track) -> Unit)? = null,
    onDeleteFromDevice: ((Track) -> Unit)? = null,
    viewModel: TrackMenuViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text =
                when (message) {
                    TrackMenuMessage.Liked -> resources.getString(R.string.core_ui_liked)
                    is TrackMenuMessage.Added -> resources.getString(R.string.core_ui_added_to, message.playlist)
                    is TrackMenuMessage.AlreadyIn -> resources.getString(R.string.core_ui_already_in, message.playlist)
                    is TrackMenuMessage.Download -> downloadText(resources, message)
                }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val downloaded by viewModel.downloaded.collectAsStateWithLifecycle()
    val track = state.track ?: return
    AddToPlaylistSheet(
        playlists = playlists,
        onPick = { id ->
            viewModel.add(track, id)
            state.close()
        },
        onCreate = { name ->
            viewModel.create(track, name)
            state.close()
        },
        onDismiss = state::close,
        title = track.title,
        onHide =
            onHide?.let { hide ->
                {
                    hide(track)
                    state.close()
                }
            },
        onDeleteFromDevice =
            onDeleteFromDevice?.takeIf { track.isOnPhone() }?.let { delete ->
                {
                    delete(track)
                    state.close()
                }
            },
        onLike =
            if (showLike) {
                {
                    viewModel.like(track)
                    state.close()
                }
            } else {
                null
            },
        download =
            track.takeUnless { it.isOnPhone() }?.let {
                SheetDownload(downloaded = track.id in downloaded) {
                    viewModel.toggleDownload(track, track.id in downloaded)
                    state.close()
                }
            },
    )
}

/** Файл на телефоне, а не трек из сети: его не нужно скачивать. */
fun Track.isOnPhone(): Boolean = playbackUri.startsWith("content://")

private fun downloadText(
    resources: android.content.res.Resources,
    message: TrackMenuMessage.Download,
): String =
    when (message.start) {
        null -> resources.getString(R.string.core_ui_download_removed)
        DownloadStart.Started -> resources.getString(R.string.core_ui_download_started, message.title)
        DownloadStart.AlreadyDownloaded -> resources.getString(R.string.core_ui_download_started, message.title)
        DownloadStart.OnPhone -> resources.getString(R.string.core_ui_download_on_phone)
        DownloadStart.PreviewOnly -> resources.getString(R.string.core_ui_download_preview)
    }

sealed interface TrackMenuMessage {
    data object Liked : TrackMenuMessage

    data class Added(
        val playlist: String,
    ) : TrackMenuMessage

    data class AlreadyIn(
        val playlist: String,
    ) : TrackMenuMessage

    /** [start] null — загрузка удалена. */
    data class Download(
        val title: String,
        val start: DownloadStart?,
    ) : TrackMenuMessage
}

@HiltViewModel
class TrackMenuViewModel
    @Inject
    constructor(
        private val repository: PlaylistRepository,
        private val reactions: ReactionRepository,
        private val online: OnlineSearch,
        private val downloads: Downloads,
    ) : ViewModel() {
        val downloaded: StateFlow<Set<TrackId>> =
            downloads
                .observe()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

        val playlists: StateFlow<ImmutableList<Playlist>> =
            repository
                .observePlaylists()
                .map { it.toImmutableList() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), persistentListOf())

        private val channel = Channel<TrackMenuMessage>(Channel.BUFFERED)
        val messages = channel.receiveAsFlow()

        fun like(track: Track) {
            viewModelScope.launch {
                reactions.set(keep(track).id, Reaction.Like)
                channel.send(TrackMenuMessage.Liked)
            }
        }

        fun toggleDownload(
            track: Track,
            downloaded: Boolean,
        ) {
            viewModelScope.launch {
                if (downloaded) {
                    downloads.remove(track.id)
                    channel.send(TrackMenuMessage.Download(track.title, null))
                } else {
                    channel.send(TrackMenuMessage.Download(track.title, downloads.start(keep(track))))
                }
            }
        }

        fun add(
            track: Track,
            playlist: PlaylistId,
        ) {
            val name = playlists.value.firstOrNull { it.id == playlist }?.name.orEmpty()
            viewModelScope.launch { addTo(track, playlist, name) }
        }

        fun create(
            track: Track,
            name: String,
        ) {
            viewModelScope.launch { addTo(track, repository.create(name), name.trim()) }
        }

        private suspend fun addTo(
            track: Track,
            playlist: PlaylistId,
            name: String,
        ) {
            val added = repository.addTracks(playlist, listOf(keep(track).id)) > 0
            channel.send(if (added) TrackMenuMessage.Added(name) else TrackMenuMessage.AlreadyIn(name))
        }

        /** Превью Deezer в коллекции бесполезно: если в подключениях есть полная версия, сохраняется она. */
        private suspend fun keep(track: Track): Track = online.fullVersions(listOf(track)).firstOrNull() ?: track

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
