package dev.fuzz.connect.manifest

/** Одна запись журнала: без секретов, только путь, итог и время. */
data class RequestLogEntry(
    val atMs: Long,
    val path: String,
    /** HTTP-код или null, если ответа не было (таймаут, нет сети). */
    val status: Int?,
    val durationMs: Long,
    val error: String? = null,
)

/** Последние запросы подключения для экрана «Подключения» (отладка, кнопка «Проверить»). */
internal class RequestJournal(private val capacity: Int = DEFAULT_CAPACITY) {
    private val entries = ArrayDeque<RequestLogEntry>()

    fun add(entry: RequestLogEntry) =
        synchronized(entries) {
            entries.addLast(entry)
            while (entries.size > capacity) entries.removeFirst()
        }

    /** Новые сверху. */
    fun snapshot(): List<RequestLogEntry> = synchronized(entries) { entries.reversed() }

    private companion object {
        const val DEFAULT_CAPACITY = 30
    }
}
