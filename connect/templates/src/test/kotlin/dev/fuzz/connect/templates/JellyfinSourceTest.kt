package dev.fuzz.connect.templates

import dev.fuzz.connect.templates.jellyfin.JellyfinSource
import dev.fuzz.core.domain.plugin.ExternalId
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.Providers
import dev.fuzz.core.domain.plugin.TrackRef
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinSourceTest {
    private val auth = """{"AccessToken":"tok1","User":{"Id":"u1","Name":"sasha"},"ServerId":"x"}"""
    private val items = """{"Items":[{"Id":"i1","Name":"Aerials","Album":"Toxicity","AlbumArtist":"SOAD",
        "Artists":["System of a Down"],"RunTimeTicks":2350000000,"Type":"Audio"}],"TotalRecordCount":1}"""

    private fun source(server: TestServer) =
        JellyfinSource(server.client, "https://jf.example/", "sasha", "sesame", deviceId = "dev-1")

    @Test
    fun logsInOnceThenSearchesWithToken() =
        runBlocking {
            val server =
                TestServer { req ->
                    if (req.url.encodedPath.endsWith("AuthenticateByName")) json(auth) else json(items)
                }
            val jf = source(server)
            val track = (jf.search("aerials", 10) as PluginResult.Ok).value.tracks.single()
            jf.search("aerials", 10)

            assertEquals("Aerials", track.title)
            assertEquals("System of a Down", track.artist)
            assertEquals(235_000L, track.durationMs)
            assertEquals(ExternalId(Providers.JELLYFIN, "i1"), track.externalIds.single())
            assertEquals(3, server.requests.size)
            val login = server.requests.first()
            val body = String((login.body as OutgoingContent.ByteArrayContent).bytes())
            assertTrue(body, "\"Pw\":\"sesame\"" in body)
            val search = server.requests[1]
            assertEquals("/Users/u1/Items", search.url.encodedPath)
            assertEquals("Audio", search.url.parameters["IncludeItemTypes"])
            assertTrue("Token=\"tok1\"" in search.headers[HttpHeaders.Authorization]!!)
        }

    @Test
    fun reLoginsOnceWhenTokenRevoked() =
        runBlocking {
            var searches = 0
            val server =
                TestServer { req ->
                    when {
                        req.url.encodedPath.endsWith("AuthenticateByName") -> json(auth)
                        searches++ == 0 -> respond("", HttpStatusCode.Unauthorized)
                        else -> json(items)
                    }
                }
            assertTrue(source(server).search("x", 1) is PluginResult.Ok)
            assertEquals(2, server.requests.count { it.url.encodedPath.endsWith("AuthenticateByName") })
        }

    @Test
    fun wrongPasswordIsUnauthorized() =
        runBlocking {
            val server = TestServer { respond("", HttpStatusCode.Unauthorized) }
            val result = source(server).search("x", 1)
            assertEquals(PluginResult.Kind.UNAUTHORIZED, (result as PluginResult.Failure).kind)
        }

    @Test
    fun playbackUriIsStaticStreamWithToken() =
        runBlocking {
            val server = TestServer { json(auth) }
            val ref = TrackRef("x", "y", externalIds = listOf(ExternalId(Providers.JELLYFIN, "i1")))
            val uri = (source(server).playbackUri(ref) as PluginResult.Ok).value
            assertEquals("https://jf.example/Audio/i1/stream?static=true&api_key=tok1", uri)
        }
}
