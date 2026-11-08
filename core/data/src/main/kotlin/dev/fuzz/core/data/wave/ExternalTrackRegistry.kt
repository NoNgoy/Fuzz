package dev.fuzz.core.data.wave

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
    constructor() {
        private class Entry(
            val source: MusicSource,
            val ref: TrackRef,
            val origin: TrackOrigin,
        )

        private val entries = ConcurrentHashMap<TrackId, Entry>()

        fun remember(
            id: TrackId,
            source: MusicSource,
            ref: TrackRef,
            origin: TrackOrigin,
        ) {
            if (entries.size >= MAX_ENTRIES) entries.clear()
            entries[id] = Entry(source, ref, origin)
        }

        /** Трек для плеера или null, если источник не дал ссылку (нет сети, нет превью). */
        suspend fun resolve(id: TrackId): Track? {
            val entry = entries[id] ?: return null
            val uri = (entry.source.playbackUri(entry.ref) as? PluginResult.Ok)?.value ?: return null
            return Track(
                id = id,
                title = entry.ref.title,
                artistName = entry.ref.artist,
                albumTitle = entry.ref.album,
                durationMs = entry.ref.durationMs ?: 0L,
                playbackUri = uri,
                format = if (entry.origin == TrackOrigin.Preview) PREVIEW_LABEL else entry.source.displayName,
            )
        }

        private companion object {
            /** Пул сессии — сотни треков; предел защищает от роста за долгие дни без перезапуска. */
            const val MAX_ENTRIES = 5_000
            const val PREVIEW_LABEL = "Превью"
        }
    }
