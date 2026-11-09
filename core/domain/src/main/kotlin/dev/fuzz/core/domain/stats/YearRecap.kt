package dev.fuzz.core.domain.stats

/** Итоги года по истории прослушиваний. Треки вне коллекции идут в часы, но не в списки лучших. */
data class YearRecap(
    val year: Int,
    val listenedMs: Long = 0,
    val plays: Int = 0,
    val distinctTracks: Int = 0,
    val topTracks: List<RecapTrack> = emptyList(),
    val topArtists: List<RecapArtist> = emptyList(),
    /** Месяц, в котором слушали больше всего, 1–12; null, если слушали мало. */
    val topMonth: Int? = null,
    val newArtists: Int = 0,
) {
    val isEmpty: Boolean get() = plays == 0
}

data class RecapTrack(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUri: String?,
    val plays: Int,
)

data class RecapArtist(
    val name: String,
    val plays: Int,
    val listenedMs: Long,
)

interface YearRecapRepository {
    /** Итоги текущего года: в декабре это настоящие итоги, раньше — «пока что». */
    suspend fun currentYear(): YearRecap
}
