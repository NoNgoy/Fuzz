package dev.fuzz.core.player.session

import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId

/**
 * Перевод между [Track] и [MediaItem].
 *
 * MediaController не передаёт в сессию localConfiguration (URI), поэтому URI едет в
 * requestMetadata.mediaUri, а сессия восстанавливает его в onAddMediaItems.
 */
internal object MediaItemMapper {
    private const val EXTRA_DURATION = "fuzz.duration"
    private const val EXTRA_FORMAT = "fuzz.format"

    fun toMediaItem(track: Track): MediaItem {
        val uri = track.playbackUri.toUri()
        return MediaItem
            .Builder()
            .setMediaId(track.id.value)
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(
                MediaMetadata
                    .Builder()
                    .setTitle(track.title)
                    .setArtist(track.artistName)
                    .setAlbumTitle(track.albumTitle)
                    .setArtworkUri(track.artworkUri?.toUri())
                    .setTrackNumber(track.trackNumber)
                    .setRecordingYear(track.year)
                    .setDurationMs(track.durationMs.takeIf { it > 0 })
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setExtras(bundleOf(EXTRA_DURATION to track.durationMs, EXTRA_FORMAT to track.format))
                    .build(),
            ).build()
    }

    /** Возвращает URI, потерянный при передаче через MediaController. */
    fun withRestoredUri(item: MediaItem): MediaItem {
        if (item.localConfiguration != null) return item
        val uri = item.requestMetadata.mediaUri ?: return item
        return item.buildUpon().setUri(uri).build()
    }

    fun toTrack(item: MediaItem): Track {
        val meta = item.mediaMetadata
        val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri
        return Track(
            id = TrackId(item.mediaId),
            title = meta.title?.toString().orEmpty(),
            artistName = meta.artist?.toString().orEmpty(),
            albumTitle = meta.albumTitle?.toString(),
            durationMs = meta.durationMs ?: meta.extras?.getLong(EXTRA_DURATION) ?: 0L,
            playbackUri = uri?.toString().orEmpty(),
            artworkUri = meta.artworkUri?.toString(),
            trackNumber = meta.trackNumber,
            year = meta.recordingYear,
            format = meta.extras?.getString(EXTRA_FORMAT),
        )
    }

    fun browsable(
        id: String,
        title: String,
        mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
    ): MediaItem =
        MediaItem
            .Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata
                    .Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(mediaType)
                    .build(),
            ).build()
}
