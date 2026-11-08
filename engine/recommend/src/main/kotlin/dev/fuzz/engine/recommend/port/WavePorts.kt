package dev.fuzz.engine.recommend.port

import dev.fuzz.engine.recommend.model.TrackHint
import dev.fuzz.engine.recommend.model.TrackOrigin
import dev.fuzz.engine.recommend.model.WaveTrack
import dev.fuzz.engine.recommend.model.Weighted

/**
 * Порты движка волны. Их реализует core:data поверх Room и плагинов meta:* и source:*,
 * движок о Last.fm, ListenBrainz, Jamendo и Deezer ничего не знает. Любой порт может бросить
 * исключение или долго молчать: сессия отрезает его по таймауту, и волна работает на остальных.
 */
interface WaveLibrary {
    /** Вся коллекция с признаками (файлы и скачанное). Должна отвечать из базы за десятки миллисекунд. */
    suspend fun tracks(): List<WaveTrack>
}

/**
 * Граф похожести из Last.fm и ListenBrainz (теги и похожие складываются с весами в core:data).
 * Без сети отвечает из кэша метаданных или пустым списком.
 */
interface SimilarityGraph {
    suspend fun similarArtists(
        artist: String,
        limit: Int,
    ): List<Weighted<String>>

    suspend fun similarTracks(
        track: TrackHint,
        limit: Int,
    ): List<Weighted<TrackHint>>
}

/** Каталог вне коллекции: Jamendo ([TrackOrigin.OpenCatalog]) или превью Deezer ([TrackOrigin.Preview]). */
interface ExternalCatalog {
    val origin: TrackOrigin

    suspend fun byArtist(
        artist: String,
        limit: Int,
    ): List<WaveTrack>

    suspend fun byTags(
        tags: List<String>,
        limit: Int,
        offset: Int,
    ): List<WaveTrack>

    /** Найти конкретный трек из подсказки «похожие треки». */
    suspend fun find(hint: TrackHint): WaveTrack?
}
