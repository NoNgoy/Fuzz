package dev.fuzz.core.data.wave

import dev.fuzz.core.domain.player.PlaybackHeaders
import dev.fuzz.core.domain.plugin.AuthorizedPlayback
import dev.fuzz.core.domain.plugin.MusicSource
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId
import dev.fuzz.engine.recommend.model.TrackOrigin
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Треки вне коллекции, которые внешние каталоги отдали волне. Движок знает их только по [TrackId],
 * а чтобы сыграть трек, нужен исходный [TrackRef] и источник, который выдаст ссылку на поток.
 */
@Singleton
class ExternalTrackRegistry
    @Inject
    constructor() : PlaybackHeaders {
        /** Откуда трек: источник, его описание у источника и вид каталога. */
        class Entry(
            val source: MusicSource,
            val ref: TrackRef,
            val origin: TrackOrigin,
        ) {
            val label: String get() = if (origin == TrackOrigin.Preview) PREVIEW_LABEL else source.displayName
        }

        private val entries = ConcurrentHashMap<TrackId, Entry>()

        /** Заголовки для ссылок источников с [AuthorizedPlayback], по ссылке на поток. */
        private val headers = ConcurrentHashMap<String, Map<String, String>>()

        override fun forUri(uri: String): Map<String, String> = headers[uri].orEmpty()

        fun remember(
            id: TrackId,
            source: MusicSource,
            ref: TrackRef,
            origin: TrackOrigin,
        ) {
            if (entries.size >= MAX_ENTRIES) entries.clear()
            entries[id] = Entry(source, ref, origin)
        }

        fun entry(id: TrackId): Entry? = entries[id]

        /** Трек для плеера или null, если источник не дал ссылку (нет сети, нет превью). */
        suspend fun resolve(id: TrackId): Track? {
            val entry = entries[id] ?: return null
            val uri = (entry.source.playbackUri(entry.ref) as? PluginResult.Ok)?.value ?: return null
            (entry.source as? AuthorizedPlayback)?.let { source ->
                if (headers.size >= MAX_ENTRIES) headers.clear()
                headers[uri] = source.playbackHeaders(entry.ref)
            }
            return Track(
                id = id,
                title = entry.ref.title,
                artistName = entry.ref.artist,
                albumTitle = entry.ref.album,
                durationMs = entry.ref.durationMs ?: 0L,
                playbackUri = uri,
                format = entry.label,
            )
        }

        private companion object {
            /** Пул сессии — сотни треков; предел защищает от роста за долгие дни без перезапуска. */
            const val MAX_ENTRIES = 5_000
            const val PREVIEW_LABEL = "Превью"
        }
    }
