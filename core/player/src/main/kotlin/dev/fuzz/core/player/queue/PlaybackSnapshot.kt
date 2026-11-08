package dev.fuzz.core.player.queue

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.fuzz.core.model.TrackId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Снимок очереди для восстановления после перезапуска (раздел 3.2 ТЗ). */
@Serializable
data class PlaybackSnapshot(
    val trackIds: List<String>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean = false,
    val repeatMode: Int = 0,
) {
    val ids: List<TrackId> get() = trackIds.map(::TrackId)
}

/** Хранит снимок в маленьком файле: пишется атомарно, читается один раз при старте сервиса. */
@Singleton
class PlaybackSnapshotStore
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val json = Json { ignoreUnknownKeys = true }
        private val file: File get() = File(context.filesDir, FILE_NAME)

        suspend fun save(snapshot: PlaybackSnapshot) =
            withContext(Dispatchers.IO) {
                val tmp = File(context.filesDir, "$FILE_NAME.tmp")
                tmp.writeText(json.encodeToString(PlaybackSnapshot.serializer(), snapshot))
                tmp.renameTo(file)
            }

        suspend fun load(): PlaybackSnapshot? =
            withContext(Dispatchers.IO) {
                try {
                    file.takeIf { it.exists() }?.readText()?.let {
                        json.decodeFromString(PlaybackSnapshot.serializer(), it)
                    }
                } catch (e: IOException) {
                    Log.w(TAG, "Не удалось прочитать снимок очереди", e)
                    null
                } catch (e: SerializationException) {
                    Log.w(TAG, "Снимок очереди повреждён", e)
                    null
                }
            }

        private companion object {
            const val TAG = "PlaybackSnapshot"
            const val FILE_NAME = "playback_snapshot.json"
        }
    }
