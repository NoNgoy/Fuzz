package dev.fuzz.connect.templates.jellyfin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AuthRequest(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val password: String,
)

@Serializable
internal data class AuthResponse(
    @SerialName("AccessToken") val accessToken: String,
    @SerialName("User") val user: UserDto,
)

@Serializable
internal data class UserDto(
    @SerialName("Id") val id: String,
)

@Serializable
internal data class ItemsResponse(
    @SerialName("Items") val items: List<ItemDto> = emptyList(),
)

@Serializable
internal data class ItemDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String = "",
    @SerialName("Album") val album: String? = null,
    @SerialName("AlbumArtist") val albumArtist: String? = null,
    @SerialName("Artists") val artists: List<String> = emptyList(),
    /** Длительность в тиках по 100 нс. */
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
)
