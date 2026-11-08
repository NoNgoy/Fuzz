package dev.fuzz.importer.formats

import dev.fuzz.core.domain.plugin.PlaylistImporter
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.TrackRef
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.charset.Charset

class ImportersTest {
    private suspend fun PlaylistImporter.parse(
        text: String,
        charset: Charset = Charsets.UTF_8,
    ): List<TrackRef> = (import(text.toByteArray(charset).inputStream()) as PluginResult.Ok).value

    @Test
    fun `m3u with extinf and bare paths`() =
        runTest {
            val tracks =
                M3uImporter().parse(
                    """
                    #EXTM3U
                    #EXTINF:238,Deftones - Change (In the House of Flies)
                    /sdcard/Music/Deftones/03 Change.flac
                    /storage/emulated/0/Music/03%20-%20Кино%20-%20Кукушка.mp3
                    """.trimIndent(),
                )

            assertEquals(
                listOf(
                    TrackRef("Change (In the House of Flies)", "Deftones", durationMs = 238_000),
                    TrackRef("Кукушка", "Кино"),
                ),
                tracks,
            )
        }

    @Test
    fun `m3u from windows in cp1251`() =
        runTest {
            val m3u = "#EXTINF:-1,Кино - Группа крови\nC:\\Music\\x.mp3\n"
            val tracks = M3uImporter().parse(m3u, Charset.forName("windows-1251"))
            assertEquals(listOf(TrackRef("Группа крови", "Кино")), tracks)
        }

    @Test
    fun `exportify csv with quotes and ms durations`() =
        runTest {
            val csv =
                """
                "Track URI","Track Name","Artist Name(s)","Album Name","Duration (ms)"
                "spotify:track:1","Teardrop","Massive Attack","Mezzanine","329000"
                "spotify:track:2","Change (In the House of Flies)","Deftones","White Pony, Deluxe","298000"
                """.trimIndent()

            assertEquals(
                listOf(
                    TrackRef("Teardrop", "Massive Attack", "Mezzanine", 329_000),
                    TrackRef("Change (In the House of Flies)", "Deftones", "White Pony, Deluxe", 298_000),
                ),
                CsvImporter().parse(csv),
            )
        }

    @Test
    fun `russian csv with semicolons and mm ss`() =
        runTest {
            val csv = "Исполнитель;Название;Альбом;Длительность\nКино;Кукушка;Чёрный альбом;6:37\n"
            assertEquals(listOf(TrackRef("Кукушка", "Кино", "Чёрный альбом", 397_000)), CsvImporter().parse(csv))
        }

    @Test
    fun `csv without header is read as artist and title`() =
        runTest {
            assertEquals(listOf(TrackRef("Утекай", "Мумий Тролль")), CsvImporter().parse("Мумий Тролль - Утекай\n"))
        }

    @Test
    fun `xspf with title and with location only`() =
        runTest {
            val xml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <playlist version="1" xmlns="http://xspf.org/ns/0/">
                  <trackList>
                    <track><title>Roygbiv</title><creator>Boards of Canada</creator><duration>151000</duration></track>
                    <track><location>file:///music/Radiohead%20-%20Reckoner.flac</location></track>
                  </trackList>
                </playlist>
                """.trimIndent()

            assertEquals(
                listOf(
                    TrackRef("Roygbiv", "Boards of Canada", durationMs = 151_000),
                    TrackRef("Reckoner", "Radiohead"),
                ),
                XspfImporter().parse(xml),
            )
        }

    @Test
    fun `xspf with doctype is rejected`() =
        runTest {
            val xml =
                """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]>""" +
                    "<playlist>&e;</playlist>"
            assertEquals(
                PluginResult.Kind.PARSE,
                (XspfImporter().import(xml.byteInputStream()) as PluginResult.Failure).kind,
            )
        }

    @Test
    fun `plain text list`() =
        runTest {
            val text = "• Кино — Кукушка\n\n- Radiohead – Reckoner\nTeardrop\n"
            assertEquals(
                listOf(TrackRef("Кукушка", "Кино"), TrackRef("Reckoner", "Radiohead"), TrackRef("Teardrop", "")),
                TextImporter().parse(text),
            )
        }
}
