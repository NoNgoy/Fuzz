package dev.fuzz.core.domain.wishlist

import dev.fuzz.core.model.PlaylistId
import dev.fuzz.core.model.Track
import kotlinx.coroutines.flow.Flow
import java.net.URLEncoder

/**
 * «Хочу добавить» (ТЗ, список желаний): треки, которых нет в коллекции. Когда трек появляется
 * (файл на телефоне или сохранённый трек из подключений), он сам встаёт на своё место в плейлистах.
 */
interface WishlistRepository {
    fun observe(): Flow<List<Wish>>

    fun observeCount(): Flow<Int>

    suspend fun add(requests: List<WishRequest>)

    suspend fun remove(wish: Wish)

    /** Варианты из своих серверов и плагинов. */
    suspend fun findOnline(wish: Wish): List<Track>

    /** Сохраняет найденный трек в коллекцию; желание закрывается, трек встаёт в плейлисты. */
    suspend fun take(
        wish: Wish,
        track: Track,
    )

    /** Сверяет список с коллекцией; возвращает, сколько желаний исполнилось. */
    suspend fun resolve(): Int
}

data class Wish(
    /** Все строки одного трека: он может ждать места в нескольких плейлистах. */
    val ids: List<Long>,
    val title: String,
    val artist: String,
    val album: String?,
    val reason: WishReason,
    val playlists: List<String>,
) {
    val query: String get() = "$artist $title"

    val bandcampUrl: String get() = "https://bandcamp.com/search?q=" + encode(query)

    val qobuzUrl: String get() = "https://www.qobuz.com/search?q=" + encode(query)

    private fun encode(text: String) = URLEncoder.encode(text, Charsets.UTF_8.name())
}

enum class WishReason { Import, Backup, Preview }

data class WishRequest(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
    val reason: WishReason,
    val playlistId: PlaylistId? = null,
    val position: Int? = null,
)
