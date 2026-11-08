package dev.fuzz.connect.templates.webdav

import dev.fuzz.core.domain.plugin.AuthorizedPlayback
import dev.fuzz.core.domain.plugin.ExternalId
import dev.fuzz.core.domain.plugin.MusicSource
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.Providers
import dev.fuzz.core.domain.plugin.SearchResult
import dev.fuzz.core.domain.plugin.SourceCapability
import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.core.network.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.Base64

/**
 * Папка с музыкой на WebDAV (Nextcloud, ownCloud, роутер, NAS). Индекс строится обходом PROPFIND
 * с глубиной 1 и держится в памяти до [refresh]. Поиск идёт по пути файла, артист и альбом
 * берутся из папок «Артист/Альбом/01 - Название.flac». Обход не выходит за [baseUrl] и его домен,
 * чтобы сервер не увёл пароль на чужой адрес. Пароль в URL не кладётся: плеер берёт заголовки
 * из [playbackHeaders].
 */
class WebDavSource(
    private val client: HttpClient,
    baseUrl: String,
    private val username: String? = null,
    private val password: String? = null,
    override val id: String = "webdav",
    override val displayName: String = "WebDAV",
    private val maxFolders: Int = DEFAULT_MAX_FOLDERS,
) : MusicSource,
    AuthorizedPlayback {
    override val capabilities = setOf(SourceCapability.STREAM, SourceCapability.OFFLINE_CACHE)

    private val root = Url(baseUrl.trimEnd('/') + "/")
    private val origin = root.origin()
    private val rootPath = root.encodedPath

    private val mutex = Mutex()
    private var index: List<String>? = null

    override suspend fun search(query: String, limit: Int): PluginResult<SearchResult> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        return when (val files = files()) {
            is PluginResult.Failure -> {
                files
            }

            is PluginResult.Ok -> {
                val tracks =
                    files.value.asSequence()
                        .filter { path ->
                            decode(path.removePrefix(rootPath)).lowercase().let { p -> words.all { it in p } }
                        }
                        .take(limit)
                        .map { toRef(it) }
                        .toList()
                PluginResult.Ok(SearchResult(tracks = tracks))
            }
        }
    }

    override suspend fun playbackUri(track: TrackRef): PluginResult<String> {
        val path =
            track.externalIds.firstOrNull { it.provider == Providers.WEBDAV }?.id
                ?.takeIf { it.startsWith(rootPath) }
                ?: return PluginResult.Failure(PluginResult.Kind.NOT_FOUND, "no WebDAV path")
        return PluginResult.Ok(origin + path)
    }

    override suspend fun playbackHeaders(track: TrackRef): Map<String, String> =
        basicAuth()?.let { mapOf(HttpHeaders.Authorization to it) }.orEmpty()

    /** Сбросить индекс, например после «Обновить» на экране подключения. */
    suspend fun refresh() = mutex.withLock { index = null }

    private suspend fun files(): PluginResult<List<String>> =
        mutex.withLock {
            index?.let { return@withLock PluginResult.Ok(it) }
            val result = safeCall(timeoutMs = INDEX_TIMEOUT_MS) { crawl() }
            if (result is PluginResult.Ok) {
                val files = result.value ?: return@withLock PluginResult.Failure(PluginResult.Kind.UNAUTHORIZED)
                index = files
                PluginResult.Ok(files)
            } else {
                result as PluginResult.Failure
            }
        }

    /** Обход в ширину. null, если сервер отказал в доступе. */
    private suspend fun crawl(): List<String>? {
        val queue = ArrayDeque(listOf(rootPath))
        val seen = mutableSetOf(rootPath)
        val files = mutableListOf<String>()
        var visited = 0
        while (queue.isNotEmpty() && visited < maxFolders) {
            val folder = queue.removeFirst()
            visited++
            val (folders, audio) = children(folder, propfind(folder) ?: return null)
            folders.filter(seen::add).forEach(queue::addLast)
            files += audio
        }
        return files.sorted()
    }

    /** Подпапки (со слешем на конце) и аудиофайлы внутри корня, без самой папки. */
    private fun children(folder: String, entries: List<DavEntry>): Pair<List<String>, List<String>> {
        val inside =
            entries.mapNotNull { entry -> normalize(entry.href)?.let { it to entry.isFolder } }
                .filter { (path, _) -> path.startsWith(rootPath) && path.trimEnd('/') != folder.trimEnd('/') }
        val folders = inside.filter { it.second }.map { it.first.trimEnd('/') + "/" }
        val audio =
            inside.filter { !it.second }.map { it.first }
                .filter { it.substringAfterLast('.').lowercase() in AUDIO_EXTENSIONS }
        return folders to audio
    }

    private suspend fun propfind(folder: String): List<DavEntry>? {
        val response =
            client.request(origin + folder) {
                method = PROPFIND
                header("Depth", "1")
                basicAuth()?.let { header(HttpHeaders.Authorization, it) }
                setBody(TextContent(PROPFIND_BODY, ContentType.Application.Xml))
            }
        if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) return null
        if (response.status != HttpStatusCode.MultiStatus) throw IOException("WebDAV HTTP ${response.status.value}")
        return MultiStatus.parse(response.bodyAsText())
    }

    /** href бывает полным URL или путём; чужой домен отбрасываем. */
    private fun normalize(href: String): String? =
        when {
            href.startsWith("/") -> {
                href
            }

            href.startsWith("http") -> {
                runCatching {
                    Url(href)
                }.getOrNull()?.takeIf { it.origin() == origin }?.encodedPath
            }

            else -> {
                null
            }
        }

    private fun toRef(path: String): TrackRef {
        val segments = decode(path.removePrefix(rootPath)).split('/').filter { it.isNotEmpty() }
        val file = segments.last().substringBeforeLast('.').replace(TRACK_NUMBER, "").trim()
        val folders = segments.dropLast(1)
        val (artist, title) =
            when {
                folders.size >= 2 -> folders[folders.size - 2] to file
                " - " in file -> file.substringBefore(" - ").trim() to file.substringAfter(" - ").trim()
                else -> folders.lastOrNull().orEmpty() to file
            }
        return TrackRef(
            title = title,
            artist = artist,
            album = folders.lastOrNull()?.takeIf { folders.size >= 2 },
            externalIds = listOf(ExternalId(Providers.WEBDAV, path)),
        )
    }

    private fun Url.origin(): String {
        val port = if (specifiedPort == 0 || specifiedPort == protocol.defaultPort) "" else ":$specifiedPort"
        return "${protocol.name}://$host$port"
    }

    private fun decode(path: String) = runCatching { path.decodeURLPart() }.getOrDefault(path)

    private fun basicAuth(): String? {
        val user = username ?: return null
        val token = Base64.getEncoder().encodeToString("$user:${password.orEmpty()}".toByteArray())
        return "Basic $token"
    }

    private companion object {
        val PROPFIND = HttpMethod("PROPFIND")
        const val PROPFIND_BODY =
            """<?xml version="1.0" encoding="utf-8"?>""" +
                """<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
        const val DEFAULT_MAX_FOLDERS = 2_000
        const val INDEX_TIMEOUT_MS = 120_000L
        val AUDIO_EXTENSIONS = setOf("mp3", "flac", "ogg", "oga", "opus", "m4a", "aac", "wav")
        val TRACK_NUMBER = Regex("""^\d{1,3}[\s._-]+""")
    }
}
