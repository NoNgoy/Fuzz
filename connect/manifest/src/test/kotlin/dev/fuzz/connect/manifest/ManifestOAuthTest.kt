package dev.fuzz.connect.manifest

import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.network.RateLimiter
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestOAuthTest {
    private fun manifestJson(grant: String = "client_credentials") =
        """{
        "version": 1, "id": "oa", "name": "OAuth API", "baseUrl": "https://api.example.com",
        "auth": {"type": "oauth2", "tokenUrl": "https://accounts.example.com/token", "grant": "$grant", "scope": "read"},
        "search": {"path": "/search", "query": {"q": "{query}"}, "results": "$.items[*]",
            "map": {"id": "$.id", "title": "$.name", "artist": "$.artist"}},
        "stream": {"url": "https://api.example.com/stream/{id}"}
    }"""

    private val requests = mutableListOf<HttpRequestData>()
    private var tokens = 0
    private var rejectToken: String? = null
    private val rotated = mutableListOf<String>()

    private fun connection(grant: String = "client_credentials", secret: String = "cid:csecret"): ManifestConnection {
        val manifest = (ManifestParser.parse(manifestJson(grant)) as ManifestParser.Result.Valid).manifest
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        val engine =
            MockEngine { request ->
                requests += request
                if (request.url.host == "accounts.example.com") {
                    tokens++
                    respond(
                        """{"access_token":"at$tokens","expires_in":3600,"refresh_token":"rt$tokens"}""",
                        headers = json,
                    )
                } else if (request.headers[HttpHeaders.Authorization] == "Bearer $rejectToken") {
                    respond("", HttpStatusCode.Unauthorized)
                } else {
                    respond("""{"items":[{"id":"1","name":"Song","artist":"Band"}]}""", headers = json)
                }
            }
        return ManifestConnection(HttpClient(engine), manifest, secret, RateLimiter(0)) { rotated += it }
    }

    private fun form(request: HttpRequestData) = String((request.body as OutgoingContent.ByteArrayContent).bytes())

    @Test
    fun clientCredentialsTokenIsFetchedOnceAndSentAsBearer() =
        runBlocking {
            val c = connection()
            assertTrue(c.source.search("x", 5) is PluginResult.Ok)
            c.source.search("y", 5)

            assertEquals(1, tokens)
            val tokenRequest = requests.first()
            assertTrue(form(tokenRequest), "grant_type=client_credentials" in form(tokenRequest))
            assertTrue("scope=read" in form(tokenRequest))
            assertTrue(tokenRequest.headers[HttpHeaders.Authorization]!!.startsWith("Basic "))
            assertEquals("Bearer at1", requests[1].headers[HttpHeaders.Authorization])
            assertEquals("Bearer at1", requests[2].headers[HttpHeaders.Authorization])
        }

    @Test
    fun revokedTokenIsReplacedOnce() =
        runBlocking {
            val c = connection()
            c.source.search("x", 5)
            rejectToken = "at1"
            assertTrue(c.source.search("x", 5) is PluginResult.Ok)
            assertEquals(2, tokens)
            assertEquals("Bearer at2", requests.last().headers[HttpHeaders.Authorization])
        }

    @Test
    fun refreshTokenGrantSendsTokenAndReportsRotation() =
        runBlocking {
            val c = connection(grant = "refresh_token", secret = "cid::rt0")
            c.source.search("x", 5)
            val body = form(requests.first())
            assertTrue(
                body,
                "grant_type=refresh_token" in body && "refresh_token=rt0" in body && "client_id=cid" in body,
            )
            assertNull(requests.first().headers[HttpHeaders.Authorization])
            assertEquals(listOf("rt1"), rotated)
        }

    @Test
    fun journalRecordsRequestsWithoutSecretsAndCheckWorks() =
        runBlocking {
            val c = connection()
            assertEquals(PluginResult.Ok(1), c.check())
            val entry = c.journal().single()
            assertEquals("/search", entry.path)
            assertEquals(200, entry.status)
            assertNull(entry.error)
        }

    @Test
    fun rejectsOAuthWithoutHttpsTokenUrl() {
        val bad = manifestJson().replace("https://accounts.example.com/token", "http://accounts.example.com/token")
        val errors = (ManifestParser.parse(bad) as ManifestParser.Result.Invalid).errors
        assertTrue(errors.toString(), errors.any { "tokenUrl" in it })
        val grant = manifestJson(grant = "password")
        assertTrue(ManifestParser.parse(grant) is ManifestParser.Result.Invalid)
    }
}
