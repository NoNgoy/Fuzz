package dev.fuzz.connect.manifest

import dev.fuzz.core.domain.plugin.ArtistRef
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestExtrasTest {
    private val manifestJson = """{
        "version": 1, "id": "my-api", "name": "My API", "baseUrl": "https://api.example.com",
        "auth": {"type": "bearer"},
        "search": {"path": "/search", "query": {"q": "{query}"}, "results": "$.data[*]",
            "map": {"id": "$.id", "title": "$.name", "artist": "$.artist"}},
        "stream": {"url": "https://api.example.com/stream/{id}"},
        "lyrics": {"path": "/lyrics", "query": {"t": "{title}", "a": "{artist}", "d": "{durationSec}"},
            "synced": "$.lrc", "plain": "$.text"},
        "similar": {
            "tracks": {"path": "/similar", "query": {"track": "{id}", "n": "{limit}"}, "results": "$.items[*]",
                "title": "$.name", "artist": "$.artist", "id": "$.id", "score": "$.match", "scoreScale": 100},
            "artists": {"path": "/artists/similar", "query": {"name": "{artist}"}, "results": "$[*]",
                "artist": "$.name"}
        }
    }"""

    private val requests = mutableListOf<HttpRequestData>()

    private fun connection(routes: Map<String, String>): ManifestConnection {
        val manifest = (ManifestParser.parse(manifestJson) as ManifestParser.Result.Valid).manifest
        val engine =
            MockEngine { request ->
                requests += request
                val body = routes[request.url.encodedPath]
                if (body == null) {
                    respond("", HttpStatusCode.NotFound)
                } else {
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        return ManifestConnection(HttpClient(engine), manifest, "tok", RateLimiter(0))
    }

    private val track =
        TrackRef("Toxicity", "System of a Down", durationMs = 219_000, externalIds = listOf(ExternalId("my-api", "t1")))

    @Test
    fun syncedLyricsWinAndQueryIsSubstituted() =
        runBlocking {
            val c = connection(mapOf("/lyrics" to """{"lrc":"[00:01.00]Conversion\n[00:03.50]Software","text":"x"}"""))
            val lyrics = (c.lyrics!!.lyrics(track) as PluginResult.Ok).value
            assertTrue(lyrics.synced)
            assertEquals(listOf(1_000L, 3_500L), lyrics.lines.map { it.timeMs })
            val url = requests.single().url
            assertEquals("Toxicity", url.parameters["t"])
            assertEquals("System of a Down", url.parameters["a"])
            assertEquals("219", url.parameters["d"])
            assertEquals("Bearer tok", requests.single().headers[HttpHeaders.Authorization])
        }

    @Test
    fun plainLyricsFallbackAndNotFound() =
        runBlocking {
            val plain = connection(mapOf("/lyrics" to """{"lrc":null,"text":"line one\nline two"}"""))
            val lyrics = (plain.lyrics!!.lyrics(track) as PluginResult.Ok).value
            assertEquals(false, lyrics.synced)
            assertEquals(listOf("line one", "line two"), lyrics.lines.map { it.text })

            val missing = connection(emptyMap()).lyrics!!.lyrics(track)
            assertEquals(PluginResult.Kind.NOT_FOUND, (missing as PluginResult.Failure).kind)
        }

    @Test
    fun similarTracksUseScoreScaleAndIds() =
        runBlocking {
            val body = """{"items":[{"id":"t2","name":"Aerials","artist":"SOAD","match":80},
                {"id":"t3","artist":"no title"},{"id":"t4","name":"Spiders","artist":"SOAD","match":250}]}"""
            val similar =
                (
                    connection(
                        mapOf("/similar" to body),
                    ).metadata!!.similarTracks(track, 10) as PluginResult.Ok
                ).value
            assertEquals(listOf("Aerials", "Spiders"), similar.map { it.item.title })
            assertEquals(0.8f, similar[0].score, 0.001f)
            assertEquals(1.0f, similar[1].score, 0.001f)
            assertEquals(ExternalId("my-api", "t2"), similar[0].item.externalIds.single())
            assertEquals("t1", requests.single().url.parameters["track"])
            assertEquals("10", requests.single().url.parameters["n"])
        }

    @Test
    fun similarArtistsScoreByPositionWithoutScorePath() =
        runBlocking {
            val c = connection(mapOf("/artists/similar" to """[{"name":"Serj Tankian"},{"name":"Deftones"}]"""))
            val artists = (c.metadata!!.similarArtists(ArtistRef("System of a Down"), 5) as PluginResult.Ok).value
            assertEquals(listOf("Serj Tankian", "Deftones"), artists.map { it.item.name })
            assertEquals(1.0f, artists[0].score, 0.001f)
            assertEquals(0.5f, artists[1].score, 0.001f)
            assertEquals("System of a Down", requests.single().url.parameters["name"])
            assertTrue(c.metadata.tags(track) is PluginResult.Failure)
        }

    @Test
    fun manifestWithoutExtrasHasNoProviders() {
        val bare = manifestJson.substringBefore(",\n        \"lyrics\"") + "\n    }"
        val manifest = (ManifestParser.parse(bare) as ManifestParser.Result.Valid).manifest
        val c = ManifestConnection(HttpClient(MockEngine { respond("") }), manifest)
        assertNull(c.lyrics)
        assertNull(c.metadata)
    }

    @Test
    fun rejectsBrokenExtras() {
        val noPaths = manifestJson.replace("\"synced\": \"$.lrc\", \"plain\": \"$.text\"", "\"synced\": null")
        assertTrue(ManifestParser.parse(noPaths) is ManifestParser.Result.Invalid)
        val noTitle =
            manifestJson.replace(
                "\"title\": \"$.name\", \"artist\": \"$.artist\", \"id\"",
                "\"artist\": \"$.artist\", \"id\"",
            )
        val errors = (ManifestParser.parse(noTitle) as ManifestParser.Result.Invalid).errors
        assertTrue(errors.toString(), errors.any { "similar.tracks" in it })
        val badPath = manifestJson.replace("\"path\": \"/lyrics\"", "\"path\": \"lyrics\"")
        assertTrue(ManifestParser.parse(badPath) is ManifestParser.Result.Invalid)
    }
}
