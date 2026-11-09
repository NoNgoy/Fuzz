package dev.fuzz.core.data.lyrics

import dev.fuzz.core.data.sync.Clock
import dev.fuzz.core.database.dao.LyricsDao
import dev.fuzz.core.database.entity.LyricsEntity
import dev.fuzz.core.domain.lyrics.LyricsResult
import dev.fuzz.core.domain.plugin.Lyrics
import dev.fuzz.core.domain.plugin.LyricsLine
import javax.inject.Inject

/** Тексты на диске: что нашлось однажды, открывается и без сети. */
internal interface LyricsStore {
    suspend fun get(key: String): LyricsResult.Found?

    suspend fun put(
        key: String,
        found: LyricsResult.Found,
    )
}

internal class RoomLyricsStore
    @Inject
    constructor(
        private val dao: LyricsDao,
        private val clock: Clock,
    ) : LyricsStore {
        override suspend fun get(key: String): LyricsResult.Found? =
            dao.get(key)?.let { entity ->
                val lines =
                    entity.lines.lineSequence().filter { it.isNotEmpty() }.map { line ->
                        val time = line.substringBefore('\t').toLongOrNull()?.takeIf { it >= 0 }
                        LyricsLine(time, line.substringAfter('\t'))
                    }
                LyricsResult.Found(Lyrics(lines.toList(), entity.synced), entity.source)
            }

        override suspend fun put(
            key: String,
            found: LyricsResult.Found,
        ) {
            val lines =
                found.lyrics.lines.joinToString("\n") { line ->
                    "${line.timeMs ?: -1}\t${line.text.replace('\n', ' ').replace('\t', ' ')}"
                }
            dao.upsert(LyricsEntity(key, found.source, found.lyrics.synced, lines, clock.nowMs()))
            if (dao.count() > LIMIT) dao.trim(LIMIT)
        }

        private companion object {
            /** Несколько тысяч текстов — это единицы мегабайт. */
            const val LIMIT = 5_000
        }
    }
