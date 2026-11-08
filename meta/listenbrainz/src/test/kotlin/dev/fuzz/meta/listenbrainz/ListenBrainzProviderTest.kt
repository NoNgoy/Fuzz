package dev.fuzz.meta.listenbrainz

import dev.fuzz.core.domain.plugin.ArtistRef
import dev.fuzz.core.domain.plugin.ExternalId
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.Providers
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

class ListenBrainzProviderTest {
    private val artist = ArtistRef("SOAD", listOf(ExternalId(Providers.MUSICBRAINZ, "me")))

    private fun provider(status: HttpStatusCode, body: String): ListenBrainzProvider {
        val engine =
            MockEngine {
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        val client =
            HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        return ListenBrainzProvider(client)
    }

    @Test
    fun normalizesScoresAndSkipsSelf() =
        runBlocking {
            val body = """[{"artist_mbid":"me","name":"SOAD","score":100},
            {"artist_mbid":"a","name":"Serj","score":50},{"artist_mbid":"b","name":"Tool","score":25,"x":1}]"""
            val list = (provider(HttpStatusCode.OK, body).similarArtists(artist, 10) as PluginResult.Ok).value
            assertEquals(listOf("Serj", "Tool"), list.map { it.item.name })
            assertEquals(0.5f, list[0].score, 0.001f)
            assertEquals("a", list[0].item.externalIds.single().id)
        }

    @Test
    fun withoutMbidIsNotFound() =
        runBlocking {
            val result = provider(HttpStatusCode.OK, "[]").similarArtists(ArtistRef("x"), 5)
            assertEquals(PluginResult.Kind.NOT_FOUND, (result as PluginResult.Failure).kind)
        }

    @Test
    fun serverErrorIsFailure() =
        runBlocking {
            val result = provider(HttpStatusCode.BadGateway, "[]").similarArtists(artist, 5)
            assertTrue(result is PluginResult.Failure)
        }
}
