package dev.fuzz.source.deezerpreview

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Deezer возвращает 200 и при ошибках: тогда `data` пуст, а в теле лежит `error`. */
@Serializable
internal data class DeezerSearchResponse(val data: List<DeezerTrackDto> = emptyList())

@Serializable
internal data class DeezerTrackDto(
    /** У ответа /track/{id} с ошибкой id нет. */
    val id: Long = 0,
    val title: String = "",
    /** Длительность в секундах. */
    val duration: Long? = null,
    /** 30-секундный mp3; пустая строка, если превью недоступно. */
    val preview: String? = null,
    val artist: DeezerArtistDto? = null,
    val album: DeezerAlbumDto? = null,
)

@Serializable
internal data class DeezerArtistDto(
    val id: Long = 0,
    val name: String = "",
    @SerialName("picture_xl") val pictureXl: String? = null,
    @SerialName("picture_medium") val pictureMedium: String? = null,
    @SerialName("nb_fan") val fans: Int? = null,
)

@Serializable
internal data class DeezerAlbumDto(
    val id: Long = 0,
    val title: String = "",
    @SerialName("cover_medium") val coverMedium: String? = null,
    @SerialName("cover_xl") val coverXl: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("record_type") val recordType: String? = null,
)

@Serializable
internal data class DeezerArtistList(val data: List<DeezerArtistDto> = emptyList())

@Serializable
internal data class DeezerAlbumList(val data: List<DeezerAlbumDto> = emptyList())
