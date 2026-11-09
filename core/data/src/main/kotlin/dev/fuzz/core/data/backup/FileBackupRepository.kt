package dev.fuzz.core.data.backup

import android.content.Context
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.fuzz.core.data.search.OnlineRanking
import dev.fuzz.core.data.sync.Clock
import dev.fuzz.core.database.FuzzDatabase
import dev.fuzz.core.database.dao.BackupDao
import dev.fuzz.core.database.dao.WishDao
import dev.fuzz.core.database.entity.ListenEntity
import dev.fuzz.core.database.entity.PlaylistEntity
import dev.fuzz.core.database.entity.PlaylistTrackEntity
import dev.fuzz.core.database.entity.ReactionEntity
import dev.fuzz.core.database.entity.SavedRefEntity
import dev.fuzz.core.database.entity.TrackEntity
import dev.fuzz.core.database.entity.WishEntity
import dev.fuzz.core.datastore.UserPreferences
import dev.fuzz.core.domain.backup.BackupFormatException
import dev.fuzz.core.domain.backup.BackupRepository
import dev.fuzz.core.domain.backup.BackupSummary
import dev.fuzz.core.domain.backup.RestoreSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.Base64
import javax.inject.Inject

/**
 * Резервная копия в JSON. Файлы с телефона сопоставляются при восстановлении по исполнителю и названию:
 * на новом телефоне у них другие id. Треки из сети восстанавливаются как есть вместе с источником.
 */
internal class FileBackupRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val db: FuzzDatabase,
        private val dao: BackupDao,
        private val wishes: WishDao,
        private val preferences: DataStore<UserPreferences>,
        private val clock: Clock,
    ) : BackupRepository {
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
            }

        override suspend fun export(uri: String): BackupSummary {
            val file = snapshot()
            val text = json.encodeToString(BackupFile.serializer(), file)
            withContext(Dispatchers.IO) {
                val stream = context.contentResolver.openOutputStream(uri.toUri(), "wt") ?: throw IOException(uri)
                stream.bufferedWriter().use { it.write(text) }
            }
            return BackupSummary(
                likes = file.reactions.count { it.reaction == LIKE },
                playlists = file.playlists.size,
                listens = file.listens.size,
            )
        }

        override suspend fun restore(uri: String): RestoreSummary {
            val text =
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openInputStream(uri.toUri()) ?: throw IOException(uri)
                    stream.bufferedReader().use { it.readText() }
                }
            val file =
                try {
                    json.decodeFromString(BackupFile.serializer(), text)
                } catch (e: SerializationException) {
                    throw BackupFormatException("bad backup", e)
                } catch (e: IllegalArgumentException) {
                    throw BackupFormatException("bad backup", e)
                }
            if (file.format != BackupFile.FORMAT) throw BackupFormatException("not a Fuzz backup")
            val result = db.withTransaction { restoreData(file) }
            val passwords = restoreSettings(file)
            return result.copy(connectionsNeedingPassword = passwords)
        }

        private suspend fun snapshot(): BackupFile {
            val refs = dao.savedRefs().associateBy { it.trackId }
            val entries = dao.playlistEntries().groupBy { it.playlistId }
            val prefs = preferences.data.first()
            return BackupFile(
                createdAt = clock.nowMs(),
                tracks = dao.tracks().map { it.toBackup(refs[it.id]) },
                reactions = dao.reactions().map { BackupReaction(it.trackId, it.reaction, it.updatedAt) },
                playlists =
                    dao.playlists().map { p ->
                        BackupPlaylist(
                            p.id,
                            p.name,
                            p.createdAt,
                            p.updatedAt,
                            entries[p.id].orEmpty().map {
                                it.trackId
                            },
                        )
                    },
                listens = dao.listens().map { BackupListen(it.trackId, it.startedAt, it.playedMs, it.completed) },
                settings = Base64.getEncoder().encodeToString(prefs.forBackup().toByteArray()),
                passwordless = prefs.connectionsList.filter { !it.secret.isEmpty }.map { it.id },
            )
        }

        /** Настройки без того, что привязано к телефону: папок, онбординга и зашифрованных паролей. */
        private fun UserPreferences.forBackup(): UserPreferences =
            toBuilder()
                .clearOnboardingCompleted()
                .clearMusicFolders()
                .clearFoldersChosen()
                .clearConnections()
                .addAllConnections(connectionsList.map { it.toBuilder().clearSecret().build() })
                .build()

        private suspend fun restoreData(file: BackupFile): RestoreSummary {
            val (ids, missing) = restoreTracks(file)

            val current = dao.reactions().associateBy { it.trackId }
            val reactions =
                file.reactions.mapNotNull { r ->
                    val id = ids[r.trackId] ?: return@mapNotNull null
                    val mine = current[id]
                    ReactionEntity(id, r.reaction, r.updatedAt).takeIf { mine == null || mine.updatedAt < r.updatedAt }
                }
            dao.putReactions(reactions)

            val playlists =
                file.playlists.count { p ->
                    val entries =
                        p.tracks
                            .mapNotNull { ids[it] }
                            .distinct()
                            .mapIndexed { i, id -> PlaylistTrackEntity(p.id, i, id, p.updatedAt) }
                    dao.restorePlaylist(PlaylistEntity(p.id, p.name, p.createdAt, p.updatedAt), entries)
                }

            val heard = dao.listens().mapTo(HashSet()) { it.trackId to it.startedAt }
            val listens =
                file.listens.mapNotNull { l ->
                    val id = ids[l.trackId] ?: l.trackId
                    ListenEntity(trackId = id, startedAt = l.startedAt, playedMs = l.playedMs, completed = l.completed)
                        .takeIf { (id to l.startedAt) !in heard }
                }
            dao.insertListens(listens)
            wishes.insert(wishesFor(file, ids))
            return RestoreSummary(
                likes = reactions.count { it.reaction == LIKE },
                playlists = playlists,
                listens = listens.size,
                missingFiles = missing,
                connectionsNeedingPassword = 0,
            )
        }

        /**
         * Файлы прежнего телефона, которых здесь нет, уходят в «Хочу добавить» с местом в плейлистах:
         * появится файл или найдётся трек в подключениях — встанет на место.
         */
        private fun wishesFor(
            file: BackupFile,
            ids: Map<String, String>,
        ): List<WishEntity> {
            val missing = file.tracks.filter { it.id !in ids }.associateBy { it.id }
            if (missing.isEmpty()) return emptyList()
            val now = clock.nowMs()
            val inPlaylists = HashSet<String>()
            val slots =
                file.playlists.flatMap { p ->
                    var placed = 0
                    p.tracks.mapNotNull { trackId ->
                        val track = missing[trackId]
                        if (track == null) {
                            placed++
                            null
                        } else {
                            inPlaylists += trackId
                            track.toWish(now, p.id, placed)
                        }
                    }
                }
            val liked = file.reactions.filter { it.reaction == LIKE }.mapTo(HashSet()) { it.trackId }
            val loose =
                missing.values.filter {
                    it.id in liked && it.id !in inPlaylists
                }.map { it.toWish(now, null, null) }
            return slots + loose
        }

        private fun BackupTrack.toWish(
            now: Long,
            playlist: String?,
            position: Int?,
        ) = WishEntity(
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs.takeIf { it > 0 },
            reason = "backup",
            playlistId = playlist,
            position = position,
            addedAt = now,
        )

        /** id трека в копии → id на этом телефоне и число файлов, которых здесь нет. */
        private suspend fun restoreTracks(file: BackupFile): Pair<Map<String, String>, Int> {
            val existing = dao.tracks()
            val existingIds = existing.mapTo(HashSet()) { it.id }
            val localByKey = existing.filter { it.sourceId == LOCAL }.associateBy { key(it.artistName, it.title) }
            val ids = HashMap<String, String>()
            val online = mutableListOf<BackupTrack>()
            var missing = 0
            file.tracks.forEach { track ->
                when {
                    track.id in existingIds -> {
                        ids[track.id] = track.id
                    }

                    track.source == LOCAL -> {
                        val match = localByKey[key(track.artist, track.title)]
                        if (match != null) ids[track.id] = match.id else missing++
                    }

                    else -> {
                        online += track
                        ids[track.id] = track.id
                    }
                }
            }
            dao.insertTracks(online.map { it.toEntity() })
            dao.insertSavedRefs(
                online.mapNotNull { t ->
                    t.saved?.let { SavedRefEntity(t.id, it.sourceKey, it.origin, it.externalIds) }
                },
            )
            return ids to missing
        }

        /** Возвращает, сколько добавленных подключений ждут пароля. */
        private suspend fun restoreSettings(file: BackupFile): Int {
            val encoded = file.settings ?: return 0
            val saved =
                try {
                    UserPreferences.parseFrom(Base64.getDecoder().decode(encoded))
                } catch (e: com.google.protobuf.InvalidProtocolBufferException) {
                    throw BackupFormatException("bad settings", e)
                }
            var needPassword = 0
            preferences.updateData { current ->
                val builder = current.toBuilder()
                if (saved.hasWave()) builder.wave = saved.wave
                if (saved.hasSound()) builder.sound = saved.sound
                if (saved.theme.isNotEmpty()) builder.theme = saved.theme
                if (saved.accent.isNotEmpty()) builder.accent = saved.accent
                val known = current.connectionsList.mapTo(HashSet()) { it.id }
                val added = saved.connectionsList.filter { it.id !in known }
                builder.addAllConnections(added)
                needPassword = added.count { it.id in file.passwordless }
                builder.build()
            }
            return needPassword
        }

        private fun TrackEntity.toBackup(ref: SavedRefEntity?) =
            BackupTrack(
                id = id,
                title = title,
                artist = artistName,
                album = albumTitle,
                durationMs = durationMs,
                uri = playbackUri,
                artwork = artworkUri,
                trackNumber = trackNumber,
                year = year,
                format = format,
                addedAt = addedAt,
                source = sourceId,
                hidden = hidden,
                saved = ref?.let { BackupSavedRef(it.sourceKey, it.origin, it.externalIds) },
            )

        private fun BackupTrack.toEntity() =
            TrackEntity(
                id = id,
                title = title,
                artistName = artist,
                albumTitle = album,
                durationMs = durationMs,
                playbackUri = uri,
                artworkUri = artwork,
                trackNumber = trackNumber,
                year = year,
                format = format,
                addedAt = addedAt,
                sourceId = source,
                updatedAt = clock.nowMs(),
                hidden = hidden,
            )

        private fun key(
            artist: String,
            title: String,
        ) = OnlineRanking.normalize(artist) + "|" + OnlineRanking.normalize(title)

        private companion object {
            const val LOCAL = "local"
            const val LIKE = 1
        }
    }
