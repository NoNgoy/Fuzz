package dev.fuzz.core.domain.download

import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId
import kotlinx.coroutines.flow.Flow

/** Скачивание треков из сети на телефон, чтобы они играли без интернета. */
interface Downloads {
    /** Скачанные треки из сети (файлы телефона сюда не входят). */
    fun observe(): Flow<Set<TrackId>>

    /** Ставит трек в загрузку и сразу отвечает; сама загрузка идёт в фоне, итог приходит в [events]. */
    suspend fun start(track: Track): DownloadStart

    /** Удаляет скачанный файл; сам трек остаётся в коллекции и снова играет из сети. */
    suspend fun remove(id: TrackId)

    val events: Flow<DownloadEvent>
}

enum class DownloadStart {
    Started,
    AlreadyDownloaded,

    /** Трек и так файл на телефоне. */
    OnPhone,

    /** Только 30-секундное превью: полной версии в подключениях нет. */
    PreviewOnly,
}

data class DownloadEvent(
    val title: String,
    val success: Boolean,
)
