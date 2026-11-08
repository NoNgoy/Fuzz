package dev.fuzz.source.subsonic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SubsonicEnvelope(
    @SerialName("subsonic-response") val response: SubsonicResponse,
)

@Serializable
internal data class SubsonicResponse(
    val status: String = "failed",
    val error: SubsonicError? = null,
    val searchResult3: SearchResult3? = null,
    val song: SongDto? = null,
)

@Serializable
internal data class SubsonicError(val code: Int = 0, val message: String? = null)

@Serializable
internal data class SearchResult3(val song: List<SongDto> = emptyList())

@Serializable
internal data class SongDto(
    val id: String,
    val title: String = "",
    val artist: String = "",
    val album: String? = null,
    /** Длительность в секундах. */
    val duration: Long? = null,
)
