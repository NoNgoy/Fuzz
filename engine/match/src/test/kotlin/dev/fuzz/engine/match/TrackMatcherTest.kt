package dev.fuzz.engine.match

import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrackMatcherTest {
    private fun track(
        title: String,
        artist: String,
        durationSec: Long = 200,
    ) = Track(
        id = TrackId("$artist/$title"),
        title = title,
        artistName = artist,
        albumTitle = null,
        durationMs = durationSec * 1000,
        playbackUri = "content://$title",
    )

    private val kukushka = track("Кукушка", "Кино", 397)
    private val change = track("Change (In the House of Flies)", "Deftones", 298)
    private val teardrop = track("Teardrop", "Massive Attack", 329)
    private val toxicity = track("Toxicity", "System of a Down")
    private val chopSuey = track("Chop Suey!", "System of a Down")
    private val matcher = TrackMatcher(listOf(kukushka, change, teardrop, toxicity, chopSuey))

    private fun find(
        title: String,
        artist: String,
        durationMs: Long? = null,
    ) = matcher.match(MatchQuery(title, artist, durationMs = durationMs))

    @Test
    fun `exact match ignores case and punctuation`() {
        assertEquals(MatchResult.Found(chopSuey), find("CHOP SUEY", "system of a down"))
    }

    @Test
    fun `remaster and feat tails are ignored`() {
        assertEquals(MatchResult.Found(teardrop), find("Teardrop - 2006 Remastered", "Massive Attack"))
        assertEquals(MatchResult.Found(change), find("Change (In the House of Flies) [Remastered 2020]", "Deftones"))
        assertEquals(
            MatchResult.Found(teardrop),
            find("Teardrop (feat. Elizabeth Fraser)", "Massive Attack, Elizabeth Fraser"),
        )
    }

    @Test
    fun `transliterated artist still matches`() {
        assertEquals(MatchResult.Found(kukushka), find("Kukushka", "Kino"))
    }

    @Test
    fun `typo becomes doubtful, not silently accepted`() {
        val result = find("Toxicty", "Sistem of a Daun")
        assertTrue(result is MatchResult.Doubtful)
        assertEquals(toxicity, (result as MatchResult.Doubtful).candidates.first())
    }

    @Test
    fun `wrong duration lowers confidence`() {
        assertTrue(find("Кукушка", "Кино", durationMs = 120_000) is MatchResult.Doubtful)
        assertEquals(MatchResult.Found(kukushka), find("Кукушка", "Кино", durationMs = 395_000))
    }

    @Test
    fun `unknown track is not found`() {
        assertEquals(MatchResult.NotFound, find("Aerials", "Linkin Park"))
    }

    @Test
    fun `title only from plain text needs confirmation`() {
        assertTrue(find("Teardrop", "") is MatchResult.Doubtful)
    }
}
