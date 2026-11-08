package dev.fuzz.meta.listenbrainz

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SimilarArtistDto(
    @SerialName("artist_mbid") val artistMbid: String,
    val name: String = "",
    val score: Double = 0.0,
)
