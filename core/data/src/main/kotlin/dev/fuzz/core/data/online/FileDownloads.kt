package dev.fuzz.core.data.online

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.fuzz.core.data.sync.Clock
import dev.fuzz.core.data.wave.ExternalTrackRegistry
import dev.fuzz.core.database.dao.DownloadDao
import dev.fuzz.core.database.entity.DownloadEntity
import dev.fuzz.core.domain.download.DownloadEvent
import dev.fuzz.core.domain.download.DownloadStart
import dev.fuzz.core.domain.download.Downloads
import dev.fuzz.core.domain.lyrics.LyricsRepository
import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Загрузки в папку приложения (Android/data/dev.fuzz/files/Music/fuzz). Туда не заглядывает сканер
 * медиатеки, поэтому скачанный трек не задваивается в коллекции. Файл удаляется вместе с приложением.
 */
@Singleton
internal class FileDownloads
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dao: DownloadDao,
        private val saved: SavedOnlineTracks,
        private val registry: ExternalTrackRegistry,
        private val clock: Clock,
        private val lyrics: LyricsRepository,
    ) : Downloads {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val running = ConcurrentHashMap.newKeySet<String>()
        private val eventFlow = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = EVENT_BUFFER)

        override val events: Flow<DownloadEvent> = eventFlow.asSharedFlow()

        override fun observe(): Flow<Set<TrackId>> = dao.observeIds().map { ids -> ids.mapTo(HashSet(), ::TrackId) }

        override suspend fun start(track: Track): DownloadStart {
            val id = track.id.value
            return when {
                track.playbackUri.startsWith(DEVICE_PREFIX) -> {
                    DownloadStart.OnPhone
                }

                track.format == PREVIEW_LABEL -> {
                    DownloadStart.PreviewOnly
                }

                dao.get(id) != null -> {
                    DownloadStart.AlreadyDownloaded
                }

                else -> {
                    saved.persist(listOf(track.id))
                    if (running.add(id)) scope.launch { fetch(track) }
                    DownloadStart.Started
                }
            }
        }

        override suspend fun remove(id: TrackId) {
            val download = dao.get(id.value) ?: return
            withContext(Dispatchers.IO) { File(download.path).delete() }
            dao.delete(id.value)
        }

        @Suppress("TooGenericExceptionCaught") // любой сбой загрузки — сообщение человеку, а не падение
        private suspend fun fetch(track: Track) {
            val id = track.id.value
            val ok =
                try {
                    val url = saved.streamUrl(id) ?: throw IOException("no stream for $id")
                    val file = File(folder(), id)
                    copy(url, registry.forUri(url), file)
                    dao.upsert(DownloadEntity(id, file.path, file.length(), clock.nowMs()))
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "download failed: ${track.title}", e)
                    false
                } finally {
                    running.remove(id)
                }
            eventFlow.emit(DownloadEvent(track.title, ok))
            // Скачанный трек слушают без сети, поэтому и текст берём заранее: он сохранится на диск.
            if (ok) lyrics.lyrics(track)
        }

        private fun folder(): File {
            val root = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
            return File(root, FOLDER).apply { mkdirs() }
        }

        private fun copy(
            url: String,
            headers: Map<String, String>,
            target: File,
        ) {
            val part = File(target.path + PART_SUFFIX)
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                if (connection.responseCode !in HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
                connection.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
            } finally {
                connection.disconnect()
            }
            if (!part.renameTo(target)) throw IOException("cannot move ${part.name}")
        }

        private companion object {
            const val TAG = "FuzzDownloads"
            const val FOLDER = "fuzz"
            const val PART_SUFFIX = ".part"
            const val DEVICE_PREFIX = "content://"
            const val PREVIEW_LABEL = "Превью"
            const val TIMEOUT_MS = 30_000
            const val EVENT_BUFFER = 8
            val HTTP_OK = 200..299
        }
    }
