package dev.fuzz.connect.templates

import dev.fuzz.connect.templates.ampache.AmpacheCredentials
import dev.fuzz.connect.templates.ampache.AmpacheSource
import dev.fuzz.core.domain.plugin.ExternalId
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.Providers
import dev.fuzz.core.domain.plugin.TrackRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class AmpacheSourceTest {
    private val songs = """{"song":[{"id":"42","title":"B.Y.O.B.","artist":{"id":"7","name":"System of a Down"},
        "album":{"id":"3","name":"Mezmerize"},"time":255,"url":"https://amp.example/play/x"}]}"""

    private fun handshake(token: String) = """{"auth":"$token","api":"6.0.0"}"""

    private fun sha256(s: String) =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun source(server: TestServer, credentials: AmpacheCredentials) =
        AmpacheSource(server.client, "https://amp.example", credentials, nowSeconds = { 1_700_000_000 })

    @Test
    fun passwordHandshakeNeverSendsPassword() =
        runBlocking {
            val server =
                TestServer { req ->
                    if (req.url.parameters["action"] ==
                        "handshake"
                    ) {
                        json(handshake("s1"))
                    } else {
                        json(songs)
                    }
                }
            val amp = source(server, AmpacheCredentials.Password("sasha", "sesame"))
            val track = (amp.search("byob", 5) as PluginResult.Ok).value.tracks.single()

            assertEquals("B.Y.O.B.", track.title)
            assertEquals("System of a Down", track.artist)
            assertEquals("Mezmerize", track.album)
            assertEquals(255_000L, track.durationMs)
            assertEquals(ExternalId(Providers.AMPACHE, "42"), track.externalIds.single())
            val hs = server.requests.first().url
            assertEquals("/server/json.server.php", hs.encodedPath)
            assertEquals(sha256("1700000000" + sha256("sesame")), hs.parameters["auth"])
            assertEquals("sasha", hs.parameters["user"])
            assertTrue("sesame" !in hs.toString())
            val search = server.requests[1].url
            assertEquals("search_songs", search.parameters["action"])
            assertEquals("s1", search.parameters["auth"])
            assertEquals("byob", search.parameters["filter"])
        }

    @Test
    fun expiredSessionTriggersOneNewHandshake() =
        runBlocking {
            var handshakes = 0
            val server =
                TestServer { req ->
                    when {
                        req.url.parameters["action"] == "handshake" -> {
                            json(handshake("s${++handshakes}"))
                        }

                        req.url.parameters["auth"] == "s1" -> {
                            json("""{"error":{"errorCode":"4701","errorMessage":"Session Expired"}}""")
                        }

                        else -> {
                            json(songs)
                        }
                    }
                }
            val result = source(server, AmpacheCredentials.ApiKey("key")).search("x", 1)
            assertTrue(result is PluginResult.Ok)
            assertEquals(2, handshakes)
        }

    @Test
    fun rejectedHandshakeIsUnauthorized() =
        runBlocking {
            val server =
                TestServer { json("""{"error":{"errorCode":4742,"errorMessage":"Received Invalid Handshake"}}""") }
            val result = source(server, AmpacheCredentials.ApiKey("bad")).search("x", 1)
            assertEquals(PluginResult.Kind.UNAUTHORIZED, (result as PluginResult.Failure).kind)
        }

    @Test
    fun playbackUriUsesStreamAction() =
        runBlocking {
            val server = TestServer { json(handshake("s1")) }
            val ref = TrackRef("x", "y", externalIds = listOf(ExternalId(Providers.AMPACHE, "42")))
            val uri = (source(server, AmpacheCredentials.ApiKey("key")).playbackUri(ref) as PluginResult.Ok).value
            assertTrue(uri, uri.startsWith("https://amp.example/server/json.server.php?action=stream&auth=s1"))
            assertTrue("type=song" in uri && "id=42" in uri)
        }
}
