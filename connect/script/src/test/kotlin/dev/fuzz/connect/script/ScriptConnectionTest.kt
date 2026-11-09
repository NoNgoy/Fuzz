package dev.fuzz.connect.script

import dev.fuzz.core.domain.plugin.ExternalId
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.TrackRef
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScriptConnectionTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val logs = mutableListOf<String>()
    private val opened = mutableListOf<ScriptConnection>()

    private val engine =
        MockEngine { request ->
            requests += request
            when (request.url.encodedPath) {
                "/search" -> {
                    val body =
                        """{"items":[{"key":"a1","name":"Chop Suey!","by":"SOAD","sec":210},""" +
                            """{"key":"","name":"x"}]}"""
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }

                "/lyrics" -> {
                    respond("""{"lrc":"[00:01.00]Wake up"}""")
                }

                else -> {
                    respond("", HttpStatusCode.NotFound)
                }
            }
        }

    private val code =
        """
        const plugin = {
          async search(query, limit) {
            const r = await fuzz.http.get("https://api.music.example/search?q=" + encodeURIComponent(query), { "X-Key": "k" });
            if (r.error) throw new Error(r.error);
            fuzz.log("found", JSON.parse(r.body).items.length);
            return JSON.parse(r.body).items.map(i => ({ id: i.key, title: i.name, artist: i.by, durationMs: i.sec * 1000 }));
          },
          async streamUrl(id) { return "https://cdn.music.example/" + id + ".mp3"; },
          async lyrics(track) {
            const r = await fuzz.http.get("https://api.music.example/lyrics?t=" + encodeURIComponent(track.title));
            return { synced: JSON.parse(r.body).lrc };
          },
        };
        """.trimIndent()

    private fun connection(
        js: String = code,
        capabilities: Set<String> = setOf("source", "lyrics"),
        limits: ScriptLimits = ScriptLimits(),
    ): ScriptConnection {
        val header = ScriptHeader(1, "my-js", "My JS", listOf("music.example"), capabilities)
        return ScriptConnection(HttpClient(engine), header, js, Dispatchers.Default, limits) { logs += it }
            .also { opened += it }
    }

    @AfterEach
    fun close() = opened.forEach { it.close() }

    @Test
    fun searchRunsScriptWithHttpAndMapsTracks() =
        runBlocking {
            val tracks = (connection().source!!.search("chop suey", 5) as PluginResult.Ok).value.tracks
            assertEquals(1, tracks.size)
            assertEquals("Chop Suey!", tracks[0].title)
            assertEquals(210_000L, tracks[0].durationMs)
            assertEquals(ExternalId("my-js", "a1"), tracks[0].externalIds.single())
            assertEquals("chop suey", requests.single().url.parameters["q"])
            assertEquals("k", requests.single().headers["X-Key"])
            assertEquals(listOf("found 2"), logs)
        }

    @Test
    fun streamUrlMustStayInsideDomains() =
        runBlocking {
            val ref = TrackRef("t", "a", externalIds = listOf(ExternalId("my-js", "a1")))
            val ok = connection().source!!.playbackUri(ref)
            assertEquals(PluginResult.Ok("https://cdn.music.example/a1.mp3"), ok)

            val evil = code.replace("https://cdn.music.example/", "https://evil.example.org/")
            assertTrue(connection(evil).source!!.playbackUri(ref) is PluginResult.Failure)
        }

    @Test
    fun syncedLyricsFromScript() =
        runBlocking {
            val lyrics = (connection().lyrics!!.lyrics(TrackRef("Toxicity", "SOAD")) as PluginResult.Ok).value
            assertTrue(lyrics.synced)
            assertEquals("Wake up", lyrics.lines.single().text)
        }

    @Test
    fun foreignDomainsAndRedirectTricksAreBlocked() =
        runBlocking {
            val js =
                """
                const plugin = { async search(q) {
                  const a = await fuzz.http.get("https://evil.example.org/x");
                  const b = await fuzz.http.get("http://api.music.example/x");
                  const c = await fuzz.http.get("https://music.example.evil.org/x");
                  const d = await fuzz.http.get("https://api.music.example:8443/x");
                  return [a, b, c, d].map((r, i) => ({ id: String(i), title: r.error ? "blocked" : "open", artist: "" }));
                } };
                """.trimIndent()
            val tracks = (connection(js).source!!.search("x", 10) as PluginResult.Ok).value.tracks
            assertEquals(listOf("blocked", "blocked", "blocked", "blocked"), tracks.map { it.title })
            assertTrue(requests.isEmpty())
        }

    @Test
    fun sandboxHasNoFilesOrModules() =
        runBlocking {
            val js =
                """
                const plugin = { async search() {
                  const probes = [typeof std, typeof os, typeof require, typeof fetch, typeof XMLHttpRequest];
                  return [{ id: "1", title: probes.join(","), artist: "" }];
                } };
                """.trimIndent()
            val title = (connection(js).source!!.search("x", 1) as PluginResult.Ok).value.tracks.single().title
            assertEquals("undefined,undefined,undefined,undefined,undefined", title)
        }

    @Test
    fun infiniteLoopIsInterrupted() =
        runBlocking {
            val js = "const plugin = { async search() { while (true) {} } };"
            val result = connection(js, limits = ScriptLimits(timeoutMs = 300)).source!!.search("x", 1)
            assertTrue(result is PluginResult.Failure, result.toString())
        }

    @Test
    fun scriptErrorsAndBadShapesAreFailures() =
        runBlocking {
            val throws = "const plugin = { async search() { throw new Error('boom'); } };"
            assertTrue(connection(throws).source!!.search("x", 1) is PluginResult.Failure)
            val wrongShape = "const plugin = { async search() { return 42; } };"
            assertTrue(connection(wrongShape).source!!.search("x", 1) is PluginResult.Failure)
            val broken = "const plugin = {"
            assertTrue(connection(broken).source!!.search("x", 1) is PluginResult.Failure)
        }

    @Test
    fun capabilitiesControlWhatIsExposed() {
        val onlyLyrics = connection(capabilities = setOf("lyrics"))
        assertEquals(null, onlyLyrics.source)
        assertTrue(onlyLyrics.lyrics != null)
    }

    @Test
    fun headerValidation() {
        val ok = """{"version":1,"id":"my-js","name":"X","domains":["music.example"]}"""
        assertTrue(ScriptHeaderParser.parse(ok, code) is ScriptHeaderParser.Result.Valid)
        val bad = """{"version":2,"id":"Bad Id","name":"","domains":["*.evil", "http://x.org"],"capabilities":["fs"]}"""
        val errors = (ScriptHeaderParser.parse(bad, "") as ScriptHeaderParser.Result.Invalid).errors
        assertTrue(errors.size >= 6, errors.toString())
    }
}
