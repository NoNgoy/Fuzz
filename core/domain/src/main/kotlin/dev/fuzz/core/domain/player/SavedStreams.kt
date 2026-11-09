package dev.fuzz.core.domain.player

/**
 * Треки из сети, сохранённые в коллекцию, лежат в базе со ссылкой `fuzz://saved/<id>`. Ссылки серверов
 * и плагинов живут недолго, поэтому плеер спрашивает настоящую ссылку на поток прямо перед загрузкой.
 */
interface SavedStreams {
    /** Ссылка на поток или null, если источник недоступен (удалён, нет сети). */
    suspend fun resolve(trackId: String): String?

    companion object {
        const val SCHEME = "fuzz"
        const val HOST = "saved"

        fun uriFor(trackId: String): String = "$SCHEME://$HOST/$trackId"
    }
}
