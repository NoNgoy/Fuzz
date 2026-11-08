package dev.fuzz.core.testing

import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId

/** Треки для превью и скриншотов. Обложек нет: экраны рисуют заглушку. */
object SampleTracks {
    private fun track(
        id: Int,
        title: String,
        artist: String,
        durationSec: Long,
        format: String,
    ) = Track(
        id = TrackId("local:$id"),
        title = title,
        artistName = artist,
        albumTitle = null,
        durationMs = durationSec * MS,
        playbackUri = "content://media/external/audio/media/$id",
        format = format,
    )

    val all: List<Track> =
        listOf(
            track(1, "Change (In the House of Flies)", "Deftones", 238, "FLAC"),
            track(2, "Teardrop", "Massive Attack", 329, "MP3"),
            track(3, "Утекай", "Мумий Тролль", 236, "FLAC"),
            track(4, "Weird Fishes / Arpeggi", "Radiohead", 318, "FLAC"),
            track(5, "Roygbiv", "Boards of Canada", 151, "MP3"),
            track(6, "Morning Static", "Lazy Hours", 204, "Jamendo"),
            track(7, "Группа крови", "Кино", 285, "FLAC"),
        )

    private const val MS = 1000L
}
