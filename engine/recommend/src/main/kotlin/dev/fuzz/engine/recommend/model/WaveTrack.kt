package dev.fuzz.engine.recommend.model

import dev.fuzz.core.common.text.TextNormalizer
import dev.fuzz.core.model.AlbumId
import dev.fuzz.core.model.TrackId

/** Где лежит трек: в коллекции (файл или скачанное), в Jamendo или только как превью. */
enum class TrackOrigin { Collection, OpenCatalog, Preview }

/** Аудиопризнаки из анализа на устройстве. [energy] и [valence] в 0..1, valence ~ мажор/минор и «светлость». */
data class AudioFeatures(
    val bpm: Float,
    val energy: Float,
    val valence: Float,
)

/**
 * Трек глазами движка волны: только то, что нужно для оценки. Экраны получают обычный Track по [id].
 * [tags] — нормализованные теги и жанры с весом 0..1 (Last.fm, MusicBrainz, свои).
 */
data class WaveTrack(
    val id: TrackId,
    val title: String,
    val artist: String,
    val album: String? = null,
    val albumId: AlbumId? = null,
    val durationMs: Long = 0,
    val origin: TrackOrigin = TrackOrigin.Collection,
    val tags: Map<String, Float> = emptyMap(),
    val language: String? = null,
    val audio: AudioFeatures? = null,
    /** Популярность 0..1 по слушателям Last.fm/ListenBrainz, если известна. */
    val popularity: Float? = null,
) {
    val artistKey: String = keyOf(artist)
    val titleKey: String = keyOf(title)

    companion object {
        /** Ключ для сравнения имён: «Систем оф Даун» и «System of a Down» сводятся к одному виду. */
        fun keyOf(name: String): String = TextNormalizer.phonetic(name)
    }
}

/** Подсказка «похожий трек» от провайдера метаданных: только строки, без ID Fuzz. */
data class TrackHint(
    val artist: String,
    val title: String,
)

data class Weighted<T>(
    val item: T,
    val weight: Float,
)
