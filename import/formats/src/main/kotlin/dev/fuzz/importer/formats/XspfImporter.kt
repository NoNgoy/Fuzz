package dev.fuzz.importer.formats

import dev.fuzz.core.domain.plugin.PlaylistImporter
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.TrackRef
import org.w3c.dom.Element
import org.xml.sax.SAXException
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** XSPF (VLC, Foobar и др.): title, creator, album, duration в мс, а без названия — имя файла из location. */
class XspfImporter : PlaylistImporter {
    override val id = "xspf"
    override val displayName = "XSPF"
    override val formats = setOf("xspf")

    override suspend fun import(input: InputStream): PluginResult<List<TrackRef>> {
        val document =
            try {
                factory().newDocumentBuilder().parse(input)
            } catch (e: SAXException) {
                return PluginResult.Failure(PluginResult.Kind.PARSE, e.message)
            }
        val nodes = document.getElementsByTagNameNS("*", "track")
        val tracks =
            (0 until nodes.length).mapNotNull { index ->
                val track = nodes.item(index) as Element
                val title = track.child("title")
                val artist = track.child("creator").orEmpty()
                val duration = track.child("duration")?.toLongOrNull()?.takeIf { it > 0 }
                when {
                    title != null -> TrackRef(title, artist, track.child("album"), duration)
                    else -> track.child("location")?.let { parseArtistTitle(fileName(it), duration) }
                }
            }
        return PluginResult.Ok(tracks)
    }

    private fun Element.child(name: String): String? {
        val list = getElementsByTagNameNS("*", name)
        return (0 until list.length)
            .map { list.item(it) }
            .firstOrNull { it.parentNode == this }
            ?.textContent
            ?.trim()
            ?.ifEmpty { null }
    }

    /** Без внешних сущностей: файл приходит от пользователя, XXE не нужен. */
    private fun factory() =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
}
