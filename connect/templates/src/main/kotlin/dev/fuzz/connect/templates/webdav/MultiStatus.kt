package dev.fuzz.connect.templates.webdav

import org.w3c.dom.Element
import java.io.IOException
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

internal data class DavEntry(val href: String, val isFolder: Boolean)

/** Разбор ответа PROPFIND (207 Multi-Status). Внешние сущности и DTD отключены (защита от XXE). */
internal object MultiStatus {
    private const val DAV = "DAV:"

    fun parse(xml: String): List<DavEntry> {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
                runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            }
        val doc =
            try {
                factory.newDocumentBuilder().parse(xml.byteInputStream())
            } catch (e: org.xml.sax.SAXException) {
                throw IOException("WebDAV: ответ не XML", e)
            }
        val responses = doc.getElementsByTagNameNS(DAV, "response")
        return (0 until responses.length).mapNotNull { i ->
            val response = responses.item(i) as Element
            val href = response.firstText("href") ?: return@mapNotNull null
            val isFolder = response.getElementsByTagNameNS(DAV, "collection").length > 0
            DavEntry(href.trim(), isFolder)
        }
    }

    private fun Element.firstText(name: String): String? =
        getElementsByTagNameNS(DAV, name).item(0)?.textContent
}
