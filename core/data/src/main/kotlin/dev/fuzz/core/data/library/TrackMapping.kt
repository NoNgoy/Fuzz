package dev.fuzz.core.data.library

import dev.fuzz.core.database.entity.TrackEntity
import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId

internal fun TrackEntity.toModel(): Track =
    Track(
        id = TrackId(id),
        title = title,
        artistName = artistName,
        albumTitle = albumTitle,
        durationMs = durationMs,
        playbackUri = playbackUri,
        artworkUri = artworkUri,
        trackNumber = trackNumber,
        year = year,
        addedAtMs = addedAt,
        format = format,
    )
