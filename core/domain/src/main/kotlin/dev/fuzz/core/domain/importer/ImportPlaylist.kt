package dev.fuzz.core.domain.importer

import dev.fuzz.core.domain.library.LibraryRepository
import dev.fuzz.core.domain.playlist.PlaylistRepository
import dev.fuzz.core.domain.plugin.PlaylistImporter
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.core.model.PlaylistId
import dev.fuzz.core.model.Track
import kotlinx.coroutines.flow.first
import java.io.InputStream
import javax.inject.Inject

/** Строка чужого плейлиста и что с ней сталось. Порядок строк сохраняется. */
sealed interface ImportItem {
    val ref: TrackRef

    data class Found(
        override val ref: TrackRef,
        val track: Track,
    ) : ImportItem

    data class Doubtful(
        override val ref: TrackRef,
        val candidates: List<Track>,
    ) : ImportItem

    data class NotFound(
        override val ref: TrackRef,
    ) : ImportItem
}

/** Сопоставление с коллекцией. Реализация в core:data поверх engine:match. */
interface PlaylistMatcher {
    fun match(
        refs: List<TrackRef>,
        library: List<Track>,
    ): List<ImportItem>
}

sealed interface ImportOutcome {
    data class Parsed(
        val name: String,
        val format: String,
        val items: List<ImportItem>,
    ) : ImportOutcome

    data class Unsupported(
        val extension: String,
        val supported: Set<String>,
    ) : ImportOutcome

    data class Failed(
        val kind: PluginResult.Kind,
    ) : ImportOutcome

    data object Empty : ImportOutcome
}

/** Читает файл плейлиста подходящим форматом и сопоставляет строки с коллекцией. */
class ImportPlaylistUseCase
    @Inject
    constructor(
        private val importers: Set<@JvmSuppressWildcards PlaylistImporter>,
        private val library: LibraryRepository,
        private val matcher: PlaylistMatcher,
    ) {
        val supportedExtensions: Set<String> get() = importers.flatMapTo(sortedSetOf()) { it.formats }

        suspend operator fun invoke(
            fileName: String,
            input: InputStream,
        ): ImportOutcome {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            val importer =
                importers.firstOrNull { extension in it.formats }
                    ?: return ImportOutcome.Unsupported(extension, supportedExtensions)
            return when (val result = importer.import(input)) {
                is PluginResult.Failure -> {
                    ImportOutcome.Failed(result.kind)
                }

                is PluginResult.Ok -> {
                    if (result.value.isEmpty()) {
                        ImportOutcome.Empty
                    } else {
                        ImportOutcome.Parsed(
                            name = fileName.substringBeforeLast('.').trim(),
                            format = importer.displayName,
                            items = matcher.match(result.value, library.observeTracks().first()),
                        )
                    }
                }
            }
        }
    }

/** Сохраняет выбранные треки новым плейлистом. */
class SaveImportedPlaylistUseCase
    @Inject
    constructor(
        private val playlists: PlaylistRepository,
    ) {
        suspend operator fun invoke(
            name: String,
            tracks: List<Track>,
        ): PlaylistId {
            val id = playlists.create(name)
            playlists.addTracks(id, tracks.map { it.id })
            return id
        }
    }
