package dev.fuzz.core.player.session

import dev.fuzz.core.domain.library.LibraryRepository
import dev.fuzz.core.domain.playlist.PlaylistRepository
import dev.fuzz.core.domain.reaction.ReactionRepository
import dev.fuzz.core.domain.search.SearchLibraryUseCase
import dev.fuzz.core.model.PlaylistId
import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId
import kotlinx.coroutines.flow.first

/** Узел дерева для Android Auto и часов в терминах модели, без Media3. */
internal sealed interface BrowseNode {
    val id: String

    data class Folder(
        override val id: String,
        val title: String,
        val kind: FolderKind,
    ) : BrowseNode

    /** Кнопка запуска волны: не раскрывается, а играет. */
    data class Wave(
        override val id: String,
        val title: String,
    ) : BrowseNode

    /** Трек внутри списка. [id] помнит список, чтобы при выборе играл весь список с этого трека. */
    data class Song(
        override val id: String,
        val track: Track,
    ) : BrowseNode
}

internal enum class FolderKind { Mixed, Playlist, Playlists }

/** Что сыграть по запросу из машины. */
internal sealed interface PlayPlan {
    data object Wave : PlayPlan

    data class Queue(
        val tracks: List<Track>,
        val startIndex: Int,
    ) : PlayPlan

    data object Nothing : PlayPlan
}

internal data class BrowseTitles(
    val root: String,
    val wave: String,
    val liked: String,
    val playlists: String,
    val allTracks: String,
)

/**
 * Дерево медиатеки для внешних пультов: «Моя волна», «Мне нравится», плейлисты, все треки.
 *
 * Android Auto при выборе трека присылает только его id. Чтобы играл весь список, а не один трек,
 * id трека в дереве содержит id списка: `fuzz:in/<список>|<трек>`.
 */
internal class BrowseCatalog(
    private val titles: BrowseTitles,
    private val library: LibraryRepository,
    private val playlists: PlaylistRepository,
    private val reactions: ReactionRepository,
    private val search: SearchLibraryUseCase,
) {
    val root: BrowseNode.Folder = BrowseNode.Folder(ROOT, titles.root, FolderKind.Mixed)

    suspend fun children(parentId: String): List<BrowseNode> =
        when {
            parentId == ROOT -> {
                listOf(
                    BrowseNode.Wave(WAVE, titles.wave),
                    BrowseNode.Folder(LIKED, titles.liked, FolderKind.Playlist),
                    BrowseNode.Folder(PLAYLISTS, titles.playlists, FolderKind.Playlists),
                    BrowseNode.Folder(ALL_TRACKS, titles.allTracks, FolderKind.Playlist),
                )
            }

            parentId == PLAYLISTS -> {
                playlists.observePlaylists().first().map {
                    BrowseNode.Folder(PLAYLIST_PREFIX + it.id.value, it.name, FolderKind.Playlist)
                }
            }

            else -> {
                tracksOf(parentId)?.map { BrowseNode.Song(songId(parentId, it.id), it) }.orEmpty()
            }
        }

    /** Трек по id из дерева или по обычному id трека. */
    suspend fun track(mediaId: String): Track? {
        val id = parseSong(mediaId)?.second ?: TrackId(mediaId)
        return library.getTracks(listOf(id)).firstOrNull()
    }

    /** План по одному выбранному элементу: волна, список с выбранного трека или весь список. */
    suspend fun plan(mediaId: String): PlayPlan {
        val song = parseSong(mediaId)
        val list = if (song == null && mediaId != WAVE) tracksOf(mediaId) else null
        return when {
            mediaId == WAVE -> PlayPlan.Wave
            song != null -> fromList(song.first, song.second)
            list != null -> if (list.isEmpty()) PlayPlan.Nothing else PlayPlan.Queue(list, 0)
            else -> single(TrackId(mediaId))
        }
    }

    private suspend fun fromList(
        parent: String,
        trackId: TrackId,
    ): PlayPlan {
        val tracks = tracksOf(parent).orEmpty()
        val index = tracks.indexOfFirst { it.id == trackId }
        return if (index >= 0) PlayPlan.Queue(tracks, index) else single(trackId)
    }

    /** Голосовое «включи …»: пустой запрос значит «что-нибудь», то есть волну. */
    suspend fun planForQuery(query: String): PlayPlan {
        if (query.isBlank()) return PlayPlan.Wave
        val found = search.once(query).tracks
        return if (found.isEmpty()) PlayPlan.Nothing else PlayPlan.Queue(found, 0)
    }

    suspend fun searchTracks(query: String): List<BrowseNode.Song> =
        search.once(query).tracks.map { BrowseNode.Song(it.id.value, it) }

    private suspend fun single(id: TrackId): PlayPlan =
        library.getTracks(listOf(id)).takeIf { it.isNotEmpty() }?.let { PlayPlan.Queue(it, 0) } ?: PlayPlan.Nothing

    private suspend fun tracksOf(listId: String): List<Track>? =
        when {
            listId == ALL_TRACKS -> {
                library.observeTracks().first()
            }

            listId == LIKED -> {
                val ids = reactions.observeLikedOrdered().first()
                val byId = library.getTracks(ids).associateBy { it.id }
                ids.mapNotNull(byId::get)
            }

            listId.startsWith(PLAYLIST_PREFIX) -> {
                playlists.observeTracks(PlaylistId(listId.removePrefix(PLAYLIST_PREFIX))).first()
            }

            else -> {
                null
            }
        }

    companion object {
        /** Все id дерева начинаются так; id треков из библиотеки так не начинаются. */
        private const val PREFIX = "fuzz:"
        const val ROOT = "fuzz:root"
        const val WAVE = "fuzz:wave"
        const val LIKED = "fuzz:liked"
        const val PLAYLISTS = "fuzz:playlists"
        const val ALL_TRACKS = "fuzz:all_tracks"
        private const val PLAYLIST_PREFIX = "fuzz:playlist/"
        private const val SONG_PREFIX = "fuzz:in/"

        /** Разделитель не встречается ни в id списков, ни в UUID и id треков MediaStore. */
        private const val SEPARATOR = "|"

        fun isCatalogId(mediaId: String): Boolean = mediaId.startsWith(PREFIX)

        fun songId(
            parentId: String,
            trackId: TrackId,
        ): String = SONG_PREFIX + parentId + SEPARATOR + trackId.value

        fun parseSong(mediaId: String): Pair<String, TrackId>? {
            if (!mediaId.startsWith(SONG_PREFIX)) return null
            val body = mediaId.removePrefix(SONG_PREFIX)
            val cut = body.lastIndexOf(SEPARATOR)
            if (cut <= 0 || cut == body.lastIndex) return null
            return body.substring(0, cut) to TrackId(body.substring(cut + 1))
        }
    }
}
