package dev.fuzz.core.data.search

import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineRankingTest {
    private fun track(
        title: String,
        artist: String,
    ) = Track(
        id = TrackId("$artist-$title"),
        title = title,
        artistName = artist,
        albumTitle = null,
        durationMs = 0,
        playbackUri = "",
    )

    private fun sorted(
        query: String,
        vararg tracks: Track,
    ) = OnlineRanking.sort(query, tracks.toList()) { it }.map { "${it.artistName} — ${it.title}" }

    @Test
    fun `the band comes before songs that only mention it`() {
        assertEquals(
            listOf(
                "Disturbed — Down with the Sickness",
                "Disturbed — The Sound of Silence",
                "TMS Underscores — Disturbed",
            ),
            sorted(
                "Disturbed",
                track("Disturbed", "TMS Underscores"),
                track("Down with the Sickness", "Disturbed"),
                track("The Sound of Silence", "Disturbed"),
            ),
        )
    }

    @Test
    fun `artist and title in one query win`() {
        assertEquals(
            "Disturbed — Prayer",
            sorted(
                "disturbed prayer",
                track("Prayer (By Disturbed) (Karaoke)", "ZZang KARAOKE"),
                track("Prayer", "Disturbed"),
            ).first(),
        )
    }

    @Test
    fun `case and yo do not matter`() {
        assertEquals(
            "Ёлка — Прованс",
            sorted("елка", track("Ёлочка", "Хор"), track("Прованс", "Ёлка")).first(),
        )
    }
}
