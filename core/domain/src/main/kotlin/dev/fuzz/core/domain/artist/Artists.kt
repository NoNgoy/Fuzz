package dev.fuzz.core.domain.artist

import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.core.model.Track

/** Артист в каталоге метаданных: имя, фото и, если известно, число слушателей. */
data class ArtistProfile(
    val name: String,
    val pictureUrl: String? = null,
    val fans: Int? = null,
    /** Идентификатор в каталоге; без него артист ищется по имени. */
    val id: String? = null,
)

data class AlbumProfile(
    val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String? = null,
    val year: Int? = null,
)

/** Что каталог метаданных знает об артисте. Треки здесь без звука: звук ищется отдельно. */
data class ArtistDetails(
    val artist: ArtistProfile,
    val top: List<TrackRef> = emptyList(),
    val albums: List<AlbumProfile> = emptyList(),
    val related: List<ArtistProfile> = emptyList(),
)

/** Каталог артистов и альбомов (Deezer). Ошибки сети дают пустые ответы, а не исключения. */
interface ArtistDirectory {
    suspend fun searchArtists(
        query: String,
        limit: Int,
    ): List<ArtistProfile>

    /** null, если артиста не нашли или сеть недоступна. */
    suspend fun details(artist: ArtistProfile): ArtistDetails?

    suspend fun albumTracks(album: AlbumProfile): List<TrackRef>
}

/** Страница артиста целиком: каталог плюс то, что есть в коллекции. */
data class ArtistPage(
    val artist: ArtistProfile,
    val popular: List<Track> = emptyList(),
    val albums: List<AlbumProfile> = emptyList(),
    val related: List<ArtistProfile> = emptyList(),
)

interface ArtistPages {
    suspend fun searchArtists(query: String): List<ArtistProfile>

    suspend fun page(artist: ArtistProfile): ArtistPage

    suspend fun album(album: AlbumProfile): List<Track>

    /** Все треки артиста из подключений и каталогов, страницами для подгрузки при прокрутке. */
    suspend fun tracks(
        artist: String,
        page: Int,
    ): TracksPage
}

data class TracksPage(
    val tracks: List<Track>,
    /** Источники отдали непустую страницу: можно просить следующую. */
    val hasMore: Boolean,
)
