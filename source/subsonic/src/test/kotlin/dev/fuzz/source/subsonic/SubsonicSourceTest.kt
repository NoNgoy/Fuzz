package dev.fuzz.source.subsonic

import dev.fuzz.core.domain.plugin.ExternalId
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.Providers
import dev.fuzz.core.domain.plugin.TrackRef
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsonicSourceTest {
    private var lastRequest: HttpRequestData? = null

    private fun source(text: String, status: HttpStatusCode = HttpStatusCode.OK): SubsonicSource {
        val engine =
            MockEngine { request ->
                lastRequest = request
                respond(text, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        val client =
            HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        return SubsonicSource(client, "https://music.example/", "sasha", "sesame", saltProvider = { "c19b2d" })
    }

    private val ok = """{"subsonic-response":{"status":"ok","version":"1.16.1","searchResult3":{"song":[
        {"id":"s1","title":"Chop Suey!","artist":"System of a Down","album":"Toxicity","duration":210,"x":1}]}}}"""

    @Test
    fun searchSendsTokenAuthAndMapsSongs() =
        runBlocking {
            val tracks = (source(ok).search("chop", 5) as PluginResult.Ok).value.tracks
            assertEquals("Chop Suey!", tracks.single().title)
            assertEquals(210_000L, tracks.single().durationMs)
            assertEquals(ExternalId(Providers.SUBSONIC, "s1"), tracks.single().externalIds.single())
            val url = lastRequest!!.url
            assertEquals("/rest/search3.view", url.encodedPath)
            // md5("sesame" + "c19b2d") — известное значение из спецификации Subsonic
            assertEquals(url.toString(), "26719a1196d2a940705a59634eb18eab", url.parameters["t"])
            assertEquals("c19b2d", url.parameters["s"])
            assertEquals(null, url.parameters["p"])
        }

    @Test
    fun playbackUriBuildsStreamUrl() =
        runBlocking {
            val ref = TrackRef("x", "y", externalIds = listOf(ExternalId(Providers.SUBSONIC, "s1")))
            val uri = (source(ok).playbackUri(ref) as PluginResult.Ok).value
            assertTrue(uri.startsWith("https://music.example/rest/stream.view?"))
            assertTrue("id=s1" in uri)
            assertTrue("t=26719a1196d2a940705a59634eb18eab" in uri)
        }

    @Test
    fun wrongCredentialsAreFailure() =
        runBlocking {
            val error = """{"code":40,"message":"Wrong username or password"}"""
            val body = """{"subsonic-response":{"status":"failed","error":$error}}"""
            val result = source(body).search("x", 1)
            assertEquals(PluginResult.Kind.UNAUTHORIZED, (result as PluginResult.Failure).kind)
        }

    @Test
    fun httpErrorIsFailure() =
        runBlocking {
            assertTrue(source("{}", HttpStatusCode.InternalServerError).search("x", 1) is PluginResult.Failure)
        }
}
