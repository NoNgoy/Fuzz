package dev.fuzz.core.domain.plugin

/** Разбор синхронных текстов формата LRC: «[мм:сс.хх] строка», допускается несколько меток в строке. */
object LrcParser {
    private val timeTag = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private const val MS_IN_SECOND = 1_000L
    private const val MS_IN_MINUTE = 60_000L
    private const val FRACTION_SCALE = 3

    fun parse(lrc: String): List<LyricsLine> =
        lrc.lineSequence().flatMap { line ->
            val tags = timeTag.findAll(line).toList()
            if (tags.isEmpty()) {
                emptySequence()
            } else {
                val text = line.substring(tags.last().range.last + 1).trim()
                tags.asSequence().map { LyricsLine(toMs(it), text) }
            }
        }.sortedBy { it.timeMs }.toList()

    private fun toMs(tag: MatchResult): Long {
        val (min, sec, frac) = tag.destructured
        val fractionMs = frac.padEnd(FRACTION_SCALE, '0').take(FRACTION_SCALE).toLongOrNull() ?: 0L
        return min.toLong() * MS_IN_MINUTE + sec.toLong() * MS_IN_SECOND + fractionMs
    }
}
