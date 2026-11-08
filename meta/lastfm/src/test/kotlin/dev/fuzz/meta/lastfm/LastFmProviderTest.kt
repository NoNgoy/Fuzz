package dev.fuzz.meta.lastfm

import dev.fuzz.core.domain.plugin.ArtistRef
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.core.network.RateLimiter
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

class LastFmProviderTest {
    private fun provider(status: HttpStatusCode, body: String): LastFmProvider {
        val engine =
            MockEngine {
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        val client =
            HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        return LastFmProvider(client, "key", RateLimiter(0))
    }

    @Test
    fun similarArtistsParsesStringAndNumberScores() =
        runBlocking {
            val p =
                provider(
                    HttpStatusCode.OK,
                    """{"similarartists":{"artist":[{"name":"Deftones","mbid":"m1","match":"0.8"},
                {"name":"Tool","match":0.5}]}}""",
                )
            val list = (p.similarArtists(ArtistRef("SOAD"), 10) as PluginResult.Ok).value
            assertEquals(listOf("Deftones", "Tool"), list.map { it.item.name })
            assertEquals(0.8f, list[0].score, 0.001f)
            assertEquals(0.5f, list[1].score, 0.001f)
        }

    @Test
    fun similarTracksMapsArtistAndDuration() =
        runBlocking {
            val p =
                provider(
                    HttpStatusCode.OK,
                    """{"similartracks":{"track":[{"name":"T","duration":"200","match":1,"artist":{"name":"A"}}]}}""",
                )
            val track = (p.similarTracks(TrackRef("x", "y"), 5) as PluginResult.Ok).value.single().item
            assertEquals("A", track.artist)
            assertEquals(200_000L, track.durationMs)
        }

    @Test
    fun tagsParsed() =
        runBlocking {
            val p = provider(HttpStatusCode.OK, """{"toptags":{"tag":[{"name":"metal","count":100}]}}""")
            assertEquals("metal", (p.tags(TrackRef("x", "y")) as PluginResult.Ok).value.single().name)
        }

    @Test
    fun badRequestIsNotFound() =
        runBlocking {
            val p = provider(HttpStatusCode.BadRequest, """{"error":6,"message":"not found"}""")
            val result = p.similarArtists(ArtistRef("zzz"), 5)
            assertEquals(PluginResult.Kind.NOT_FOUND, (result as PluginResult.Failure).kind)
        }

    @Test
    fun serverErrorIsFailure() =
        runBlocking {
            val result = provider(HttpStatusCode.InternalServerError, "{}").tags(TrackRef("x", "y"))
            assertTrue(result is PluginResult.Failure)
        }
}
