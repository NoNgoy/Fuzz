package dev.fuzz.feature.search

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.fuzz.core.domain.artist.AlbumProfile
import dev.fuzz.core.domain.artist.ArtistProfile
import kotlinx.serialization.Serializable

/** Страница артиста. [id] — идентификатор в каталоге; без него артист ищется по имени. */
@Serializable
data class ArtistRoute(
    val name: String,
    val id: String? = null,
    val picture: String? = null,
) {
    fun toProfile() = ArtistProfile(name = name, pictureUrl = picture, id = id)
}

@Serializable
data class AlbumRoute(
    val id: String,
    val title: String,
    val artist: String,
    val cover: String? = null,
    val year: Int? = null,
) {
    fun toProfile() = AlbumProfile(id = id, title = title, artist = artist, coverUrl = cover, year = year)
}

internal fun ArtistProfile.route() = ArtistRoute(name = name, id = id, picture = pictureUrl)

internal fun AlbumProfile.route() = AlbumRoute(id = id, title = title, artist = artist, cover = coverUrl, year = year)

fun NavGraphBuilder.artistScreens(
    onBack: () -> Unit,
    onOpenArtist: (ArtistRoute) -> Unit,
    onOpenAlbum: (AlbumRoute) -> Unit,
) {
    composable<ArtistRoute> { ArtistEntry(onBack = onBack, onOpenArtist = onOpenArtist, onOpenAlbum = onOpenAlbum) }
    composable<AlbumRoute> { AlbumEntry(onBack = onBack, onOpenArtist = onOpenArtist) }
}
