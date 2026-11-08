package dev.fuzz.importer.formats

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

private val CP1251 = Charset.forName("windows-1251")

/** UTF-8, а если байты в нём не читаются, то CP1251: старые плейлисты из Windows часто в ней. */
internal fun InputStream.readTextGuessingCharset(): String {
    val bytes = readBytes()
    val decoder =
        Charsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
    val text =
        try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, CP1251)
        }
    return text.removePrefix("﻿")
}
