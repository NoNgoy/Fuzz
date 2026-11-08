package dev.fuzz.connect.templates.ampache

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class HandshakeResponse(val auth: String? = null, val error: AmpacheError? = null)

@Serializable
internal data class SongsResponse(val song: List<SongDto> = emptyList(), val error: AmpacheError? = null)

/** Код ошибки в API 6 строка, в API 5 число: читаем как JsonElement. */
@Serializable
internal data class AmpacheError(val errorCode: JsonElement? = null, val errorMessage: String? = null)

@Serializable
internal data class NamedRef(val id: String? = null, val name: String = "")

@Serializable
internal data class SongDto(
    val id: String,
    val title: String = "",
    val artist: NamedRef? = null,
    val album: NamedRef? = null,
    /** Длительность в секундах. */
    val time: Long? = null,
)
