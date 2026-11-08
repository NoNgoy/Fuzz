package dev.fuzz.core.domain.search

import dev.fuzz.core.common.text.TextNormalizer
import dev.fuzz.core.domain.library.LibraryRepository
import dev.fuzz.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class ArtistHit(
    val name: String,
    val trackCount: Int,
)

data class SearchResults(
    val artists: List<ArtistHit> = emptyList(),
    val tracks: List<Track> = emptyList(),
) {
    val isEmpty: Boolean get() = artists.isEmpty() && tracks.isEmpty()
}

/**
 * Поиск по коллекции с транслитом: «систем оф даун» находит System of a Down.
 * Нечёткий поиск с опечатками и онлайн-источники подключатся через engine:search и плагины.
 */
class SearchLibraryUseCase
    @Inject
    constructor(
        private val library: LibraryRepository,
    ) {
        operator fun invoke(query: String): Flow<SearchResults> =
            library.observeTracks().map { tracks -> search(tracks, query) }

        suspend fun once(query: String): SearchResults = search(library.observeTracks().first(), query)

        private fun search(
            tracks: List<Track>,
            query: String,
        ): SearchResults {
            val plain = TextNormalizer.normalize(query)
            if (plain.isEmpty()) return SearchResults()
            val sound = TextNormalizer.phonetic(query)

            fun matches(text: String) =
                TextNormalizer.normalize(text).contains(plain) ||
                    (sound.length >= MIN_PHONETIC && TextNormalizer.phonetic(text).contains(sound))

            val artists =
                tracks
                    .groupBy { it.artistName }
                    .filterKeys(::matches)
                    .map { (name, list) -> ArtistHit(name, list.size) }
                    .sortedByDescending { it.trackCount }
                    .take(MAX_ARTISTS)
            val found =
                tracks
                    .filter { matches(it.title) || matches(it.artistName) || it.albumTitle?.let(::matches) == true }
                    .take(MAX_TRACKS)
            return SearchResults(artists, found)
        }

        private companion object {
            const val MIN_PHONETIC = 3
            const val MAX_ARTISTS = 5
            const val MAX_TRACKS = 50
        }
    }
