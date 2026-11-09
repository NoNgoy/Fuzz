package dev.fuzz.core.domain.search

import dev.fuzz.core.domain.plugin.MusicSource
import dev.fuzz.core.model.Track

/** Откуда онлайн-трек: свой сервер из «Подключений», открытый каталог (Jamendo) или 30-секундное превью. */
enum class OnlineOrigin { Server, OpenCatalog, Preview }

/** Встроенный онлайн-каталог (Jamendo, превью Deezer). Свои серверы приходят из подключений. */
data class CatalogSource(
    val source: MusicSource,
    val origin: OnlineOrigin,
    /** Ключ внешнего ID в TrackRef, например `jamendo`. */
    val provider: String,
)

/** Найденный онлайн-трек. У [track] ещё нет ссылки на поток: её даёт [OnlineSearch.playable]. */
data class OnlineHit(
    val track: Track,
    val origin: OnlineOrigin,
)

interface OnlineSearch {
    /**
     * Ищет во всех доступных источниках из [origins]. Сбой одного источника не мешает остальным.
     * [page] с нуля: следующая страница для подгрузки при прокрутке; пустой ответ значит «больше нет».
     */
    suspend fun search(
        query: String,
        origins: Set<OnlineOrigin>,
        page: Int,
    ): List<OnlineHit>

    /** Те же треки со ссылками на поток; недоступные (нет сети, нет превью) выпадают. */
    suspend fun playable(tracks: List<Track>): List<Track>

    /**
     * Для каждого трека ищет полную версию в своих подключениях и открытых каталогах.
     * Если не нашлось, трек остаётся как был (например, превью).
     */
    suspend fun fullVersions(tracks: List<Track>): List<Track> = tracks
}
