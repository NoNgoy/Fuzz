package dev.fuzz.core.domain.library

import dev.fuzz.core.model.TrackId
import kotlinx.coroutines.flow.Flow

/** Подборки, которые собираются сами из истории, лайков и коллекции. */
enum class SmartPlaylist {
    /** Чаще всего звучало за последние 30 дней. */
    OnRepeat,

    /** Лайкнутое, что не звучало больше двух месяцев. */
    Forgotten,

    /** Попало в коллекцию за последние 30 дней, новое сверху. */
    RecentlyAdded,

    /** В коллекции, но ни разу не звучало. */
    NeverPlayed,
}

interface SmartPlaylists {
    /** Треки подборки по порядку; скрытые и удалённые не попадают. */
    fun observe(playlist: SmartPlaylist): Flow<List<TrackId>>
}
