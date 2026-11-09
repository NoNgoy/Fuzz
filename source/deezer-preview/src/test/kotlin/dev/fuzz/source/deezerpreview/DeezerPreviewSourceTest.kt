package dev.fuzz.source.deezerpreview

import dev.fuzz.core.domain.plugin.ExternalId
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.Providers
import dev.fuzz.core.domain.plugin.SourceCapability
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

class DeezerPreviewSourceTest {
    private val body = """{"data":[
        {"id":1,"title":"A","duration":200,"preview":"https://p/1.mp3","artist":{"name":"X"},"album":{"title":"L"},"z":0},
        {"id":2,"title":"B","duration":100,"preview":"","artist":{"name":"X"}}]}"""

    private fun source(status: HttpStatusCode = HttpStatusCode.OK, text: String = body): DeezerPreviewSource {
        val engine =
            MockEngine {
                respond(text, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        val client =
            HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        return DeezerPreviewSource(client)
    }

    @Test
    fun isPreviewOnly() {
        assertEquals(setOf(SourceCapability.PREVIEW_ONLY), source().capabilities)
    }

    @Test
    fun searchSkipsTracksWithoutPreview() =
        runBlocking {
            val tracks = (source().search("x", 5) as PluginResult.Ok).value.tracks
            assertEquals(listOf("A"), tracks.map { it.title })
            assertEquals(200_000L, tracks.single().durationMs)
        }

    @Test
    fun playbackUriReturnsPreview() =
        runBlocking {
            val uri = (source().playbackUri(TrackRef("A", "X")) as PluginResult.Ok).value
            assertEquals("https://p/1.mp3", uri)
        }

    @Test
    fun apiErrorBodyMeansNotFound() =
        runBlocking {
            val result = source(text = """{"error":{"type":"Exception","code":4}}""").playbackUri(TrackRef("A", "X"))
            assertEquals(PluginResult.Kind.NOT_FOUND, (result as PluginResult.Failure).kind)
        }

    @Test
    fun playbackUriUsesLinkFromSearchWithoutNetwork() =
        runBlocking {
            val requests = mutableListOf<String>()
            val source = routed(requests) { body }
            val ref = (source.search("x", 5) as PluginResult.Ok).value.tracks.single()
            requests.clear()
            assertEquals("https://p/1.mp3", (source.playbackUri(ref) as PluginResult.Ok).value)
            assertTrue(requests.isEmpty())
        }

    @Test
    fun stalePreviewIsFetchedByTrackId() =
        runBlocking {
            var clock = 0L
            val requests = mutableListOf<String>()
            val source =
                routed(requests, now = { clock }) { path ->
                    if (path == "/track/1") """{"id":1,"title":"A","preview":"https://p/fresh.mp3"}""" else body
                }
            val ref = (source.search("x", 5) as PluginResult.Ok).value.tracks.single()
            clock += 60 * 60 * 1000L
            assertEquals("https://p/fresh.mp3", (source.playbackUri(ref) as PluginResult.Ok).value)
            assertEquals("/track/1", requests.last())
        }

    @Test
    fun trackIdWithoutPreviewFallsBackToSearch() =
        runBlocking {
            val ref = TrackRef("A", "X", externalIds = listOf(ExternalId(Providers.DEEZER, "7")))
            val source =
                routed(mutableListOf()) { path ->
                    if (path == "/track/7") """{"error":{"type":"DataException","code":800}}""" else body
                }
            assertEquals("https://p/1.mp3", (source.playbackUri(ref) as PluginResult.Ok).value)
        }

    private fun routed(
        requests: MutableList<String>,
        now: () -> Long = { 0L },
        respondTo: (String) -> String,
    ): DeezerPreviewSource {
        val engine =
            MockEngine { request ->
                requests += request.url.encodedPath
                respond(
                    respondTo(request.url.encodedPath),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        return DeezerPreviewSource(client, now = now)
    }

    @Test
    fun httpErrorIsFailure() =
        runBlocking {
            assertTrue(source(HttpStatusCode.Forbidden, "{}").search("x", 1) is PluginResult.Failure)
        }
}
