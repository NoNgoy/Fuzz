package dev.fuzz.meta.musicbrainz

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ArtistSearchResponse(val artists: List<ArtistDto> = emptyList())

@Serializable
internal data class ArtistDto(val id: String, val name: String, val score: Int = 0)

@Serializable
internal data class ReleaseGroupResponse(
    @SerialName("release-groups") val releaseGroups: List<ReleaseGroupDto> = emptyList(),
)

@Serializable
internal data class ReleaseGroupDto(
    val id: String,
    val title: String,
    @SerialName("first-release-date") val firstReleaseDate: String? = null,
)
