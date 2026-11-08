package dev.fuzz.core.data.wave

import dev.fuzz.core.database.dao.SimilarityCacheDao
import dev.fuzz.core.database.entity.SimilarityCacheEntity
import dev.fuzz.engine.recommend.model.TrackHint
import dev.fuzz.engine.recommend.model.Weighted

/** Сохранённый ответ и его возраст: свежий берётся сразу, старый — только когда сеть не ответила. */
data class Stored<T>(val value: T, val storedAt: Long)

/** Постоянное хранилище ответов графа похожести. Ключи уже нормализованы графом. */
interface SimilarityStore {
    suspend fun artists(key: String): Stored<List<Weighted<String>>>?

    suspend fun putArtists(key: String, value: List<Weighted<String>>)

    suspend fun tracks(key: String): Stored<List<Weighted<TrackHint>>>?

    suspend fun putTracks(key: String, value: List<Weighted<TrackHint>>)

    /** Удалить записи старше [beforeMs]. */
    suspend fun prune(beforeMs: Long)
}

/**
 * [SimilarityStore] на Room. Ответ хранится строками «вес\tартист[\tназвание]»: формат простой, без
 * зависимостей, табуляции и переводы строк в именах заменяются пробелом. Нечитаемые строки пропускаются.
 */
class RoomSimilarityStore(
    private val dao: SimilarityCacheDao,
    private val now: () -> Long = System::currentTimeMillis,
) : SimilarityStore {
    override suspend fun artists(key: String) =
        dao.get(ARTIST, key)?.let { e -> Stored(decode(e.payload) { it.singleOrNull() }, e.storedAt) }

    override suspend fun putArtists(key: String, value: List<Weighted<String>>) =
        dao.put(SimilarityCacheEntity(ARTIST, key, encode(value) { listOf(it) }, now()))

    override suspend fun tracks(key: String) =
        dao.get(TRACK, key)?.let { e ->
            Stored(decode(e.payload) { f -> f.takeIf { it.size == 2 }?.let { TrackHint(it[0], it[1]) } }, e.storedAt)
        }

    override suspend fun putTracks(key: String, value: List<Weighted<TrackHint>>) =
        dao.put(SimilarityCacheEntity(TRACK, key, encode(value) { listOf(it.artist, it.title) }, now()))

    override suspend fun prune(beforeMs: Long) = dao.deleteOlderThan(beforeMs)

    private fun <T> encode(items: List<Weighted<T>>, fields: (T) -> List<String>): String =
        items.joinToString("\n") { w ->
            (listOf(w.weight.toString()) + fields(w.item).map { it.replace(SEPARATORS, " ") }).joinToString("\t")
        }

    private fun <T : Any> decode(payload: String, item: (List<String>) -> T?): List<Weighted<T>> =
        payload.lineSequence().filter { it.isNotEmpty() }.mapNotNull { line ->
            val parts = line.split('\t')
            val weight = parts.first().toFloatOrNull() ?: return@mapNotNull null
            item(parts.drop(1))?.let { Weighted(it, weight) }
        }.toList()

    private companion object {
        const val ARTIST = "artist"
        const val TRACK = "track"
        val SEPARATORS = Regex("[\t\r\n]")
    }
}
