package dev.fuzz.core.data.wave

import dev.fuzz.core.database.dao.TrackDao
import dev.fuzz.core.model.TrackId
import dev.fuzz.engine.recommend.model.WaveTrack
import dev.fuzz.engine.recommend.port.WaveLibrary
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Коллекция для движка волны из Room. Тегов и аудиопризнаков пока нет: их заполнят обогащение метаданными
 * и анализ на устройстве, до тех пор вкус строится по артистам, лайкам и пропускам.
 */
internal class RoomWaveLibrary
    @Inject
    constructor(
        private val tracks: TrackDao,
    ) : WaveLibrary {
        override suspend fun tracks(): List<WaveTrack> =
            tracks.observeAll().first().map {
                WaveTrack(
                    id = TrackId(it.id),
                    title = it.title,
                    artist = it.artistName,
                    album = it.albumTitle,
                    durationMs = it.durationMs,
                )
            }
    }
