package dev.fuzz.core.model

/**
 * Трек в том виде, в каком его видят экраны и плеер. Откуда он (файл, Jamendo, превью),
 * экранам знать не нужно: это решает источник через [playbackUri].
 */
data class Track(
    val id: TrackId,
    val title: String,
    val artistName: String,
    val albumTitle: String?,
    val durationMs: Long,
    /** URI, который понимает плеер: content://, file:// или https:// для стриминга. */
    val playbackUri: String,
    val artworkUri: String? = null,
    val trackNumber: Int? = null,
    val year: Int? = null,
    val artistId: ArtistId? = null,
    val albumId: AlbumId? = null,
)
