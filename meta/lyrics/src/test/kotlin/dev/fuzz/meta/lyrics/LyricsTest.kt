package dev.fuzz.meta.lyrics

import dev.fuzz.core.domain.plugin.LrcParser
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.TrackRef
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTest {
    private fun provider(status: HttpStatusCode, body: String): LrclibProvider {
        val engine =
            MockEngine {
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        val client =
            HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        return LrclibProvider(client, "FuzzTest/1.0")
    }

    private val track = TrackRef("Song", "Band", durationMs = 185_000)

    @Test
    fun parsesLrcWithMultipleTagsAndSorts() {
        val lines = LrcParser.parse("[ar:x]\n[00:12.50][00:05.5] hello\n[01:01.00]world")
        assertEquals(listOf(5_500L, 12_500L, 61_000L), lines.map { it.timeMs })
        assertEquals("hello", lines.first().text)
    }

    @Test
    fun prefersSyncedLyrics() =
        runBlocking {
            val p = provider(HttpStatusCode.OK, """{"syncedLyrics":"[00:01.00] a","plainLyrics":"a","id":1}""")
            val lyrics = (p.lyrics(track) as PluginResult.Ok).value
            assertTrue(lyrics.synced)
            assertEquals(1_000L, lyrics.lines.single().timeMs)
        }

    @Test
    fun fallsBackToPlain() =
        runBlocking {
            val p = provider(HttpStatusCode.OK, """{"syncedLyrics":null,"plainLyrics":"a\nb"}""")
            val lyrics = (p.lyrics(track) as PluginResult.Ok).value
            assertFalse(lyrics.synced)
            assertEquals(2, lyrics.lines.size)
        }

    @Test
    fun notFoundMapsToNotFound() =
        runBlocking {
            val result = provider(HttpStatusCode.NotFound, "{}").lyrics(track)
            assertEquals(PluginResult.Kind.NOT_FOUND, (result as PluginResult.Failure).kind)
        }
}
