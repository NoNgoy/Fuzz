package dev.fuzz.core.data.online

import android.net.Uri
import dev.fuzz.core.data.sync.Clock
import dev.fuzz.core.data.wave.ExternalTrackRegistry
import dev.fuzz.core.database.dao.DownloadDao
import dev.fuzz.core.database.dao.SavedRefDao
import dev.fuzz.core.database.dao.TrackDao
import dev.fuzz.core.database.dao.WishDao
import dev.fuzz.core.database.entity.SavedRefEntity
import dev.fuzz.core.database.entity.TrackEntity
import dev.fuzz.core.database.entity.WishEntity
import dev.fuzz.core.domain.connection.ConnectedSources
import dev.fuzz.core.domain.player.SavedStreams
import dev.fuzz.core.domain.plugin.ExternalId
import dev.fuzz.core.domain.plugin.MusicSource
import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.core.domain.search.CatalogSource
import dev.fuzz.core.model.TrackId
import dev.fuzz.engine.recommend.model.TrackOrigin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Треки из сети в коллекции. Лайк или плейлист сохраняют трек из поиска, волны или страницы артиста
 * в tracks со ссылкой [SavedStreams.uriFor], а в saved_refs — откуда брать поток. Так трек переживает
 * перезапуск, попадает в «Мне нравится» и плейлисты и играет, пока жив его источник.
 */
@Singleton
internal class SavedOnlineTracks
    @Inject
    constructor(
        private val tracks: TrackDao,
        private val refs: SavedRefDao,
        private val downloads: DownloadDao,
        private val wishes: WishDao,
        private val registry: ExternalTrackRegistry,
        private val connected: ConnectedSources,
        private val catalogs: List<@JvmSuppressWildcards CatalogSource>,
        private val clock: Clock,
    ) : SavedStreams {
        /** Сохраняет те из [ids], что пришли из сети и ещё не лежат в коллекции. Остальные пропускает. */
        suspend fun persist(ids: List<TrackId>) {
            val known = ids.distinct().mapNotNull { id -> registry.entry(id)?.let { id to it } }
            if (known.isEmpty()) return
            val present = tracks.getAnyByIds(known.map { it.first.value }).associateBy { it.id }
            val now = clock.nowMs()
            known.forEach { (id, entry) ->
                val existing = present[id.value]
                when {
                    existing == null -> {
                        tracks.upsert(listOf(entry.toEntity(id, now)))
                        refs.upsert(entry.toRef(id))
                        // От превью остаётся 30 секунд: полный трек человек захочет найти или купить.
                        if (entry.origin == TrackOrigin.Preview) wishes.insert(listOf(entry.toWish(now)))
                    }

                    existing.sourceId == ONLINE_SOURCE && (existing.deleted || existing.hidden) -> {
                        tracks.upsert(listOf(existing.copy(deleted = false, hidden = false, updatedAt = now)))
                    }
                }
            }
        }

        /** Скачанный файл, если он есть, иначе свежая ссылка на поток у источника. */
        override suspend fun resolve(trackId: String): String? {
            val local = downloads.get(trackId)?.path?.let(::File)?.takeIf { it.exists() }
            return local?.let { Uri.fromFile(it).toString() } ?: streamUrl(trackId)
        }

        /** Ссылка на поток у источника, мимо скачанного файла. */
        suspend fun streamUrl(trackId: String): String? {
            val id = TrackId(trackId)
            val known = registry.entry(id) != null || restore(id)
            return if (known) registry.resolve(id)?.playbackUri else null
        }

        /** После перезапуска реестр пуст: источник и описание трека берутся из базы. */
        private suspend fun restore(id: TrackId): Boolean {
            val saved = refs.get(id.value)
            val track = saved?.let { tracks.getAnyByIds(listOf(id.value)).firstOrNull() }
            val source = saved?.let { sourceFor(it.sourceKey) }
            if (saved == null || track == null || source == null) return false
            registry.remember(id, source, track.toRef(saved), originOf(saved.origin))
            return true
        }

        /** Встроенный каталог есть сразу, а подключения читаются из настроек после запуска: ждём немного. */
        private suspend fun sourceFor(key: String): MusicSource? =
            catalogs.firstOrNull { it.source.id == key }?.source
                ?: withTimeoutOrNull(SOURCES_WAIT_MS) {
                    connected.sources.first { list -> list.any { it.id == key } }
                }?.firstOrNull { it.id == key }

        private fun ExternalTrackRegistry.Entry.toEntity(
            id: TrackId,
            now: Long,
        ) = TrackEntity(
            id = id.value,
            title = ref.title,
            artistName = ref.artist,
            albumTitle = ref.album,
            durationMs = ref.durationMs ?: 0L,
            playbackUri = SavedStreams.uriFor(id.value),
            artworkUri = ref.artworkUrl,
            trackNumber = null,
            year = null,
            format = label,
            addedAt = now,
            sourceId = ONLINE_SOURCE,
            updatedAt = now,
        )

        private fun ExternalTrackRegistry.Entry.toWish(now: Long) =
            WishEntity(
                title = ref.title,
                artist = ref.artist,
                album = ref.album,
                durationMs = ref.durationMs,
                reason = "preview",
                addedAt = now,
            )

        private fun ExternalTrackRegistry.Entry.toRef(id: TrackId) =
            SavedRefEntity(
                trackId = id.value,
                sourceKey = source.id,
                origin = origin.name,
                externalIds = ref.externalIds.joinToString("\n") { "${it.provider}\t${it.id}" },
            )

        private fun TrackEntity.toRef(saved: SavedRefEntity) =
            TrackRef(
                title = title,
                artist = artistName,
                album = albumTitle,
                durationMs = durationMs.takeIf { it > 0 },
                externalIds =
                    saved.externalIds.lines().mapNotNull { line ->
                        val parts = line.split('\t', limit = 2)
                        if (parts.size == 2) ExternalId(parts[0], parts[1]) else null
                    },
                artworkUrl = artworkUri,
            )

        private fun originOf(name: String): TrackOrigin =
            TrackOrigin.entries.firstOrNull { it.name == name } ?: TrackOrigin.OpenCatalog

        companion object {
            /** source_id треков из сети: сканер телефона их не трогает. */
            const val ONLINE_SOURCE = "online"
            private const val SOURCES_WAIT_MS = 5_000L
        }
    }
