package dev.fuzz.source.jamendo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class JamendoTracksResponse(val results: List<JamendoTrackDto> = emptyList())

@Serializable
internal data class JamendoTrackDto(
    val id: String,
    val name: String,
    @SerialName("artist_name") val artistName: String = "",
    @SerialName("album_name") val albumName: String? = null,
    /** Длительность в секундах. */
    val duration: Long? = null,
    /** Прямая ссылка на потоковое аудио. */
    val audio: String? = null,
    @SerialName("audiodownload") val audioDownload: String? = null,
    @SerialName("audiodownload_allowed") val audioDownloadAllowed: Boolean = false,
)
