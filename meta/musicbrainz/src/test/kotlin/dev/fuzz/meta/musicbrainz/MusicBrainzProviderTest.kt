package dev.fuzz.meta.musicbrainz

import dev.fuzz.core.domain.plugin.ArtistRef
import dev.fuzz.core.domain.plugin.PluginResult
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

class MusicBrainzProviderTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun provider(status: HttpStatusCode = HttpStatusCode.OK, vararg bodies: String): MusicBrainzProvider {
        val queue = ArrayDeque(bodies.toList())
        val engine = MockEngine { respond(queue.removeFirst(), status, json) }
        val client =
            HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        return MusicBrainzProvider(client, "FuzzTest/1.0", dev.fuzz.core.network.RateLimiter(0))
    }

    @Test
    fun discographyResolvesArtistThenAlbums() =
        runBlocking {
            val p =
                provider(
                    HttpStatusCode.OK,
                    """{"artists":[{"id":"a1","name":"System of a Down","score":100}]}""",
                    """{"release-groups":[{"id":"rg1","title":"Toxicity","first-release-date":"2001-09-04","x":1}]}""",
                )
            val result = p.discography(ArtistRef("System of a Down"))
            val albums = (result as PluginResult.Ok).value
            assertEquals("Toxicity", albums.single().title)
            assertEquals(2001, albums.single().year)
        }

    @Test
    fun unknownArtistIsNotFound() =
        runBlocking {
            val p = provider(HttpStatusCode.OK, """{"artists":[]}""")
            val result = p.discography(ArtistRef("zzz"))
            assertEquals(PluginResult.Kind.NOT_FOUND, (result as PluginResult.Failure).kind)
        }

    @Test
    fun serverErrorBecomesNetworkFailure() =
        runBlocking {
            val p = provider(HttpStatusCode.ServiceUnavailable, "{}")
            val result = p.discography(ArtistRef("x"))
            assertTrue(result is PluginResult.Failure)
        }
}
