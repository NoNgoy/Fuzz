package dev.fuzz.connect.manifest

import dev.fuzz.core.domain.plugin.ExternalId
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.core.network.RateLimiter
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestTest {
    private val manifestJson = """{
        "version": 1, "id": "my-api", "name": "My API", "baseUrl": "https://api.example.com",
        "auth": {"type": "apiKey", "in": "query", "name": "key"},
        "search": {"path": "/search", "query": {"q": "{query}", "n": "{limit}"}, "results": "$.data[*]",
            "map": {"id": "$.id", "title": "$.name", "artist": "$.artist.name", "durationMs": "$.sec",
                    "durationMultiplier": 1000}},
        "stream": {"url": "https://api.example.com/stream/{id}.mp3"}
    }"""

    private val response = """{"data":[
        {"id":7,"name":"Song","artist":{"name":"Band"},"sec":185},
        {"id":8,"artist":{"name":"NoTitle"}}]}"""

    private var last: HttpRequestData? = null

    private fun parse() = (ManifestParser.parse(manifestJson) as ManifestParser.Result.Valid).manifest

    private fun source(status: HttpStatusCode = HttpStatusCode.OK, secret: String? = "k1"): ManifestSource {
        val engine =
            MockEngine { request ->
                last = request
                respond(response, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        return ManifestSource(HttpClient(engine), parse(), secret, RateLimiter(0))
    }

    @Test
    fun searchMapsFieldsAndSubstitutesQuery() =
        runBlocking {
            val tracks = (source().search("rock", 5) as PluginResult.Ok).value.tracks
            assertEquals(1, tracks.size)
            assertEquals("Song", tracks[0].title)
            assertEquals("Band", tracks[0].artist)
            assertEquals(185_000L, tracks[0].durationMs)
            assertEquals(ExternalId("my-api", "7"), tracks[0].externalIds.single())
            val url = last!!.url
            assertEquals("rock", url.parameters["q"])
            assertEquals("5", url.parameters["n"])
            assertEquals("k1", url.parameters["key"])
            assertEquals("api.example.com", url.host)
        }

    @Test
    fun unauthorizedMapsToKind() =
        runBlocking {
            val result = source(HttpStatusCode.Unauthorized).search("x", 1)
            assertEquals(PluginResult.Kind.UNAUTHORIZED, (result as PluginResult.Failure).kind)
        }

    @Test
    fun streamUrlTemplateEncodesId() =
        runBlocking {
            val ref = TrackRef("x", "y", externalIds = listOf(ExternalId("my-api", "a b/1")))
            val uri = (source(secret = null).playbackUri(ref) as PluginResult.Ok).value
            assertEquals("https://api.example.com/stream/a%20b%2F1.mp3", uri)
        }

    @Test
    fun streamUrlCarriesQueryKeyLikeSearch() =
        runBlocking {
            val ref = TrackRef("x", "y", externalIds = listOf(ExternalId("my-api", "7")))
            val uri = (source().playbackUri(ref) as PluginResult.Ok).value
            assertEquals("https://api.example.com/stream/7.mp3?key=k1", uri)
            assertTrue(source().playbackHeaders(ref).isEmpty())
        }

    @Test
    fun streamHeadersCarryBearerToken() =
        runBlocking {
            val bearer =
                manifestJson.replace(
                    "{\"type\": \"apiKey\", \"in\": \"query\", \"name\": \"key\"}",
                    "{\"type\": \"bearer\"}",
                )
            val manifest = (ManifestParser.parse(bearer) as ManifestParser.Result.Valid).manifest
            val source = ManifestSource(HttpClient(MockEngine { respond("{}") }), manifest, "t1", RateLimiter(0))
            val ref = TrackRef("x", "y", externalIds = listOf(ExternalId("my-api", "7")))
            assertEquals(mapOf("Authorization" to "Bearer t1"), source.playbackHeaders(ref))
            assertEquals("https://api.example.com/stream/7.mp3", (source.playbackUri(ref) as PluginResult.Ok).value)
        }

    @Test
    fun pathPlaceholdersAreSubstituted() =
        runBlocking {
            val withPath = manifestJson.replace("\"path\": \"/search\"", "\"path\": \"/search/{query}\"")
            val manifest = (ManifestParser.parse(withPath) as ManifestParser.Result.Valid).manifest
            val engine =
                MockEngine { request ->
                    last = request
                    respond(response, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            ManifestSource(HttpClient(engine), manifest, "k1", RateLimiter(0)).search("a b/c", 1)
            assertEquals("/search/a%20b%2Fc", last!!.url.encodedPath)
        }

    @Test
    fun rejectsInsecureAndForeignHosts() {
        val http = manifestJson.replace("https://api.example.com\"", "http://api.example.com\"")
        assertTrue(ManifestParser.parse(http) is ManifestParser.Result.Invalid)
        val foreign = manifestJson.replace("https://api.example.com/stream", "https://evil.example.org/stream")
        val result = ManifestParser.parse(foreign) as ManifestParser.Result.Invalid
        assertTrue(result.errors.any { "домен" in it })
    }

    @Test
    fun rejectsUnknownFieldsBadVersionAndBadPaths() {
        assertTrue(
            ManifestParser.parse(
                manifestJson.replace("\"version\": 1", "\"version\": 2"),
            ) is ManifestParser.Result.Invalid,
        )
        assertTrue(
            ManifestParser.parse(
                manifestJson.replace("\"id\": \"my-api\",", "\"id\": \"my-api\", \"script\": \"x\","),
            ) is ManifestParser.Result.Invalid,
        )
        assertTrue(ManifestParser.parse(manifestJson.replace("$.data[*]", "data")) is ManifestParser.Result.Invalid)
        assertTrue(ManifestParser.parse("not json") is ManifestParser.Result.Invalid)
    }

    @Test
    fun jsonPathSubset() {
        val root = Json.parseToJsonElement("""{"a":{"b":[{"c":1},{"c":2}]},"x y":3}""")
        assertEquals(2, JsonPathLite.select(root, "$.a.b[*]").size)
        assertEquals("2", JsonPathLite.select(root, "$.a.b[1].c").single().toString())
        assertEquals("3", JsonPathLite.select(root, "$['x y']").single().toString())
        assertTrue(JsonPathLite.select(root, "$.nope.deeper").isEmpty())
        assertFalse(JsonPathLite.isValid("$..c"))
        assertNull(JsonPathLite.select(root, "$.a.b[5]").firstOrNull())
    }
}
