package dev.fuzz.meta.lastfm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
internal data class SimilarArtistsResponse(val similarartists: SimilarArtists? = null)

@Serializable
internal data class SimilarArtists(val artist: List<SimilarArtistDto> = emptyList())

@Serializable
internal data class SimilarArtistDto(val name: String, val mbid: String? = null, val match: JsonPrimitive? = null)

@Serializable
internal data class SimilarTracksResponse(val similartracks: SimilarTracks? = null)

@Serializable
internal data class SimilarTracks(val track: List<SimilarTrackDto> = emptyList())

@Serializable
internal data class SimilarTrackDto(
    val name: String,
    val mbid: String? = null,
    val match: JsonPrimitive? = null,
    val duration: JsonPrimitive? = null,
    val artist: LastFmArtistName,
)

@Serializable
internal data class LastFmArtistName(val name: String)

@Serializable
internal data class TopTagsResponse(val toptags: TopTags? = null)

@Serializable
internal data class TopTags(val tag: List<TagDto> = emptyList())

@Serializable
internal data class TagDto(val name: String, val count: Int = 0)
