package dev.fuzz.source.jamendo

import dev.fuzz.core.domain.plugin.ExternalId
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.Providers
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
import org.junit.Assert.assertTrue
import org.junit.Test

class JamendoSourceTest {
    private val body = """{"results":[{"id":"7","name":"Song","artist_name":"Band","album_name":"LP",
        "duration":185,"audio":"https://stream/7.mp3","extra":1}]}"""

    private fun source(status: HttpStatusCode = HttpStatusCode.OK): JamendoSource {
        val engine =
            MockEngine {
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        val client =
            HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        return JamendoSource(client, "test-id")
    }

    @Test
    fun searchMapsTracks() =
        runBlocking {
            val tracks = (source().search("song", 5) as PluginResult.Ok).value.tracks
            assertEquals("Song", tracks.single().title)
            assertEquals(185_000L, tracks.single().durationMs)
            assertEquals(ExternalId(Providers.JAMENDO, "7"), tracks.single().externalIds.single())
        }

    @Test
    fun playbackUriUsesStreamUrl() =
        runBlocking {
            val ref = TrackRef("Song", "Band", externalIds = listOf(ExternalId(Providers.JAMENDO, "7")))
            assertEquals("https://stream/7.mp3", (source().playbackUri(ref) as PluginResult.Ok).value)
        }

    @Test
    fun trackWithoutJamendoIdIsNotFound() =
        runBlocking {
            val result = source().playbackUri(TrackRef("x", "y"))
            assertEquals(PluginResult.Kind.NOT_FOUND, (result as PluginResult.Failure).kind)
        }

    @Test
    fun httpErrorIsFailure() =
        runBlocking {
            assertTrue(source(HttpStatusCode.TooManyRequests).search("a", 1) is PluginResult.Failure)
        }
}
