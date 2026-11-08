package dev.fuzz.core.domain.plugin

import java.io.InputStream

/** Базовый контракт любого подключения (раздел 8.2 ТЗ). Домен без Android-зависимостей. */
interface Connection {
    val id: String
    val displayName: String
}

interface MusicSource : Connection {
    val capabilities: Set<SourceCapability>

    suspend fun search(query: String, limit: Int = 20): PluginResult<SearchResult>

    suspend fun playbackUri(track: TrackRef): PluginResult<String>

    suspend fun download(track: TrackRef): PluginResult<InputStream> =
        PluginResult.Failure(PluginResult.Kind.UNAVAILABLE_IN_REGION, "download not supported")
}

/** Источник, умеющий искать треки по тегам (жанрам и настроениям), например Jamendo. */
interface TagSearchable {
    suspend fun byTags(tags: List<String>, limit: Int, offset: Int): PluginResult<SearchResult>
}

interface MetadataProvider : Connection {
    suspend fun similarArtists(artist: ArtistRef, limit: Int = 20): PluginResult<List<Similar<ArtistRef>>>

    suspend fun similarTracks(track: TrackRef, limit: Int = 20): PluginResult<List<Similar<TrackRef>>>

    suspend fun tags(track: TrackRef): PluginResult<List<Tag>>

    suspend fun discography(artist: ArtistRef): PluginResult<List<AlbumRef>>

    suspend fun coverUrl(album: AlbumRef): PluginResult<String>
}

interface LyricsProvider : Connection {
    suspend fun lyrics(track: TrackRef): PluginResult<Lyrics>
}

interface PlaylistImporter : Connection {
    val formats: Set<String>

    suspend fun import(input: InputStream): PluginResult<List<TrackRef>>
}

/** Корзина кандидатов для волны. */
interface CandidateGenerator : Connection {
    suspend fun candidates(seed: List<TrackRef>, limit: Int): PluginResult<List<TrackRef>>
}

/**
 * Источник, чей поток требует заголовков (например, Basic у WebDAV). Плеер добавляет их к запросу
 * ссылки из [MusicSource.playbackUri], чтобы пароль не попадал в URL и логи.
 */
interface AuthorizedPlayback {
    suspend fun playbackHeaders(track: TrackRef): Map<String, String>
}
