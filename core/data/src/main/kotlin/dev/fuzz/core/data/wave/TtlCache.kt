package dev.fuzz.core.data.wave

/** Небольшой кэш ответов метаданных с временем жизни и вытеснением старых записей. Потокобезопасен. */
internal class TtlCache<K, V : Any>(
    private val ttlMs: Long,
    private val maxSize: Int = DEFAULT_MAX_SIZE,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Entry<V>(val value: V, val storedAt: Long)

    private val map =
        object : LinkedHashMap<K, Entry<V>>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>) = size > maxSize
        }

    fun get(key: K): V? =
        synchronized(map) {
            val entry = map[key] ?: return null
            if (now() - entry.storedAt > ttlMs) {
                map.remove(key)
                null
            } else {
                entry.value
            }
        }

    fun put(key: K, value: V) = synchronized(map) { map[key] = Entry(value, now()) }

    private companion object {
        const val DEFAULT_MAX_SIZE = 500
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}
