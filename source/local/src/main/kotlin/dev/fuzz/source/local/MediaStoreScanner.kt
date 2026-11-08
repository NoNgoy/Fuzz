package dev.fuzz.source.local

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.fuzz.core.domain.library.LocalMusicScanner
import dev.fuzz.core.domain.library.ScanResult
import dev.fuzz.core.domain.library.ScannedTrack
import dev.fuzz.core.domain.onboarding.MusicFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

internal class MediaStoreScanner
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : LocalMusicScanner {
        private val resolver: ContentResolver = context.contentResolver

        override suspend fun scan(folders: List<MusicFolder>): ScanResult {
            if (!hasAudioPermission()) return ScanResult.NoPermission
            val filter = FolderFilter.of(folders) ?: return ScanResult.Success(emptyList())
            return withContext(Dispatchers.IO) {
                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${filter.selection}"
                val cursor =
                    resolver.query(
                        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                        PROJECTION,
                        selection,
                        filter.args.toTypedArray(),
                        null,
                    )
                // null бывает, когда MediaProvider недоступен: это не «музыки нет», коллекцию не трогаем.
                cursor?.use { ScanResult.Success(it.readTracks()) } ?: ScanResult.NoPermission
            }
        }

        override fun changes(): Flow<Unit> =
            callbackFlow {
                val observer =
                    object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            trySend(Unit)
                        }
                    }
                resolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
                awaitClose { resolver.unregisterContentObserver(observer) }
            }.conflate()

        private fun hasAudioPermission(): Boolean {
            val permission =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    android.Manifest.permission.READ_MEDIA_AUDIO
                } else {
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                }
            return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }

        private fun Cursor.readTracks(): List<ScannedTrack> {
            val id = getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val displayName = getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val artist = getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val duration = getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val track = getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val year = getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val mime = getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val added = getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val volume = getColumnIndexOrThrow(MediaStore.Audio.Media.VOLUME_NAME)
            val tracks = ArrayList<ScannedTrack>(count)
            while (moveToNext()) {
                val mediaId = getLong(id)
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.getContentUri(getString(volume)), mediaId)
                tracks +=
                    ScannedTrack(
                        id = "$ID_PREFIX${getString(volume)}:$mediaId",
                        title = getString(title).tagOrNull() ?: getString(displayName).substringBeforeLast('.'),
                        artistName = getString(artist).tagOrNull() ?: UNKNOWN_ARTIST,
                        albumTitle = getString(album).tagOrNull(),
                        durationMs = getLong(duration),
                        playbackUri = uri.toString(),
                        // MediaProvider с Android 10 отдаёт встроенную обложку по пути …/albumart.
                        artworkUri = "$uri/albumart",
                        trackNumber = trackNumberOf(getInt(track)),
                        year = getInt(year).takeIf { it > 0 },
                        format = formatLabel(getString(mime)),
                        addedAtMs = TimeUnit.SECONDS.toMillis(getLong(added)),
                    )
            }
            return tracks
        }

        private companion object {
            const val ID_PREFIX = "local:"
            const val UNKNOWN_ARTIST = "Неизвестный исполнитель"

            val PROJECTION =
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.TRACK,
                    MediaStore.Audio.Media.YEAR,
                    MediaStore.Audio.Media.MIME_TYPE,
                    MediaStore.Audio.Media.DATE_ADDED,
                    MediaStore.Audio.Media.VOLUME_NAME,
                )
        }
    }
