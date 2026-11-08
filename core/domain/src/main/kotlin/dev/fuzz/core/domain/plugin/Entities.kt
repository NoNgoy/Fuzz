package dev.fuzz.core.domain.plugin

data class TrackRef(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
    val externalIds: List<ExternalId> = emptyList(),
)

data class ArtistRef(val name: String, val externalIds: List<ExternalId> = emptyList())

data class AlbumRef(
    val title: String,
    val artist: String,
    val year: Int? = null,
    val externalIds: List<ExternalId> = emptyList(),
)

data class SearchResult(
    val tracks: List<TrackRef> = emptyList(),
    val albums: List<AlbumRef> = emptyList(),
    val artists: List<ArtistRef> = emptyList(),
)

data class Similar<T>(val item: T, val score: Float)

data class Tag(val name: String, val weight: Int = 0)

data class LyricsLine(val timeMs: Long?, val text: String)

data class Lyrics(val lines: List<LyricsLine>, val synced: Boolean)

enum class SourceCapability { STREAM, DOWNLOAD, PREVIEW_ONLY, OFFLINE_CACHE }

/** Результат вызова: сбой одного API не должен ронять приложение. */
sealed interface PluginResult<out T> {
    data class Ok<T>(val value: T) : PluginResult<T>

    data class Failure(val kind: Kind, val message: String? = null) : PluginResult<Nothing>

    enum class Kind { NETWORK, RATE_LIMITED, UNAUTHORIZED, NOT_FOUND, UNAVAILABLE_IN_REGION, PARSE, TIMEOUT }
}
