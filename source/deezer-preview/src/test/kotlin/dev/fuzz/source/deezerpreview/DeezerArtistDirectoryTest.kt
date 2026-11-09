package dev.fuzz.source.deezerpreview

import dev.fuzz.core.domain.artist.AlbumProfile
import dev.fuzz.core.domain.artist.ArtistProfile
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeezerArtistDirectoryTest {
    private val responses =
        mapOf(
            "/search/artist" to
                """{"data":[{"id":7,"name":"Disturbed Sid"},
                {"id":9,"name":"Disturbed","picture_xl":"https://img/9","nb_fan":100}]}""",
            "/artist/9" to """{"id":9,"name":"Disturbed","picture_xl":"https://img/9","nb_fan":100}""",
            "/artist/9/top" to
                """{"data":[{"id":1,"title":"Down with the Sickness","duration":278,"artist":{"name":"Disturbed"},
                "album":{"title":"The Sickness","cover_medium":"https://c/1"}}]}""",
            "/artist/9/albums" to
                """{"data":[{"id":11,"title":"The Sickness","release_date":"2000-03-07","cover_xl":"https://c/11"},
                {"id":12,"title":"Immortalized","release_date":"2015-08-21"}]}""",
            "/artist/9/related" to """{"data":[{"id":20,"name":"Godsmack"}]}""",
            "/album/11/tracks" to
                """{"data":[{"id":2,"title":"Voices","duration":191,"artist":{"name":"Disturbed"}}]}""",
        )

    private fun directory(fail: Boolean = false): DeezerArtistDirectory {
        val engine =
            MockEngine { request ->
                val text = responses[request.url.encodedPath]
                if (fail || text == null) {
                    respond("", HttpStatusCode.ServiceUnavailable)
                } else {
                    respond(text, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        return DeezerArtistDirectory(client)
    }

    @Test
    fun detailsFindTheExactNameAndCollectEverything() =
        runBlocking {
            val details = directory().details(ArtistProfile("disturbed"))!!
            assertEquals("9", details.artist.id)
            assertEquals("https://img/9", details.artist.pictureUrl)
            assertEquals(listOf("Down with the Sickness"), details.top.map { it.title })
            assertEquals("https://c/1", details.top.single().artworkUrl)
            // Новые альбомы первыми.
            assertEquals(listOf(2015, 2000), details.albums.map { it.year })
            assertEquals(listOf("Godsmack"), details.related.map { it.name })
        }

    @Test
    fun albumTracksTakeCoverAndTitleFromTheAlbum() =
        runBlocking {
            val tracks = directory().albumTracks(AlbumProfile("11", "The Sickness", "Disturbed", "https://c/11"))
            assertEquals("The Sickness", tracks.single().album)
            assertEquals("https://c/11", tracks.single().artworkUrl)
        }

    @Test
    fun networkFailureGivesEmptyAnswers() =
        runBlocking {
            assertTrue(directory(fail = true).searchArtists("x", 5).isEmpty())
            assertNull(directory(fail = true).details(ArtistProfile("x", id = "9")))
        }
}
