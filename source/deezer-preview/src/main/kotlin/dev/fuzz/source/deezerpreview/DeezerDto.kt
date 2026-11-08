package dev.fuzz.source.deezerpreview

import kotlinx.serialization.Serializable

/** Deezer возвращает 200 и при ошибках: тогда `data` пуст, а в теле лежит `error`. */
@Serializable
internal data class DeezerSearchResponse(val data: List<DeezerTrackDto> = emptyList())

@Serializable
internal data class DeezerTrackDto(
    val id: Long,
    val title: String = "",
    /** Длительность в секундах. */
    val duration: Long? = null,
    /** 30-секундный mp3; пустая строка, если превью недоступно. */
    val preview: String? = null,
    val artist: DeezerArtistDto? = null,
    val album: DeezerAlbumDto? = null,
)

@Serializable
internal data class DeezerArtistDto(val name: String = "")

@Serializable
internal data class DeezerAlbumDto(val title: String = "")
