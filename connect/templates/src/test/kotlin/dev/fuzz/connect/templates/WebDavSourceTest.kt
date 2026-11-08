package dev.fuzz.connect.templates

import dev.fuzz.connect.templates.webdav.WebDavSource
import dev.fuzz.core.domain.plugin.ExternalId
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.Providers
import dev.fuzz.core.domain.plugin.TrackRef
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavSourceTest {
    private fun multistatus(vararg entries: Pair<String, Boolean>) =
        buildString {
            append("""<?xml version="1.0"?><d:multistatus xmlns:d="DAV:">""")
            for ((href, folder) in entries) {
                val type = if (folder) "<d:collection/>" else ""
                append("<d:response><d:href>$href</d:href><d:propstat><d:prop>")
                append("<d:resourcetype>$type</d:resourcetype></d:prop></d:propstat></d:response>")
            }
            append("</d:multistatus>")
        }

    private val tree =
        mapOf(
            "/dav/music/" to
                multistatus(
                    "/dav/music/" to true,
                    "/dav/music/System%20of%20a%20Down/" to true,
                    "https://evil.example/steal/" to true,
                    "/dav/other/" to true,
                    "/dav/music/Loose%20-%20Single.mp3" to false,
                ),
            "/dav/music/System%20of%20a%20Down/" to
                multistatus(
                    "/dav/music/System%20of%20a%20Down/" to true,
                    "/dav/music/System%20of%20a%20Down/Toxicity/" to true,
                ),
            "/dav/music/System%20of%20a%20Down/Toxicity/" to
                multistatus(
                    "/dav/music/System%20of%20a%20Down/Toxicity" to true,
                    "/dav/music/System%20of%20a%20Down/Toxicity/06%20-%20Chop%20Suey!.flac" to false,
                    "/dav/music/System%20of%20a%20Down/Toxicity/cover.jpg" to false,
                ),
        )

    private fun server() =
        TestServer { req ->
            val body = tree[req.url.encodedPath]
            if (body == null) {
                respond("", HttpStatusCode.NotFound)
            } else {
                respond(body, HttpStatusCode.MultiStatus, headersOf(HttpHeaders.ContentType, "application/xml"))
            }
        }

    private fun source(server: TestServer) =
        WebDavSource(server.client, "https://cloud.example/dav/music", "sasha", "sesame")

    @Test
    fun indexesFoldersAndParsesArtistAlbumTitle() =
        runBlocking {
            val server = server()
            val dav = source(server)
            val track = (dav.search("chop suey", 10) as PluginResult.Ok).value.tracks.single()
            assertEquals("Chop Suey!", track.title)
            assertEquals("System of a Down", track.artist)
            assertEquals("Toxicity", track.album)

            val loose = (dav.search("single", 10) as PluginResult.Ok).value.tracks.single()
            assertEquals("Loose", loose.artist)
            assertEquals("Single", loose.title)

            // Индекс кэшируется, чужой домен и папки вне корня не обходятся.
            assertEquals(3, server.requests.size)
            assertTrue(server.requests.all { it.url.host == "cloud.example" && it.method.value == "PROPFIND" })
            assertTrue(server.requests.all { it.headers["Depth"] == "1" })
            assertTrue(server.requests.all { it.headers[HttpHeaders.Authorization]!!.startsWith("Basic ") })
        }

    @Test
    fun playbackKeepsPasswordOutOfUrl() =
        runBlocking {
            val dav = source(server())
            val path = "/dav/music/System%20of%20a%20Down/Toxicity/06%20-%20Chop%20Suey!.flac"
            val ref = TrackRef("x", "y", externalIds = listOf(ExternalId(Providers.WEBDAV, path)))
            assertEquals("https://cloud.example$path", (dav.playbackUri(ref) as PluginResult.Ok).value)
            assertEquals("Basic c2FzaGE6c2VzYW1l", dav.playbackHeaders(ref)[HttpHeaders.Authorization])

            val outside = TrackRef("x", "y", externalIds = listOf(ExternalId(Providers.WEBDAV, "/etc/passwd")))
            assertTrue(dav.playbackUri(outside) is PluginResult.Failure)
        }

    @Test
    fun forbiddenIsUnauthorized() =
        runBlocking {
            val server = TestServer { respond("", HttpStatusCode.Unauthorized) }
            val result = source(server).search("x", 1)
            assertEquals(PluginResult.Kind.UNAUTHORIZED, (result as PluginResult.Failure).kind)
        }
}
