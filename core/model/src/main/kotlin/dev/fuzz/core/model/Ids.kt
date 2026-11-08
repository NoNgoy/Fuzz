package dev.fuzz.core.model

import java.util.UUID

/** Единый внутренний идентификатор трека (раздел 8.2 ТЗ). Внешние ID источников связываются с ним отдельно. */
@JvmInline
value class TrackId(
    val value: String,
) {
    companion object {
        fun random(): TrackId = TrackId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class AlbumId(
    val value: String,
)

@JvmInline
value class ArtistId(
    val value: String,
)
