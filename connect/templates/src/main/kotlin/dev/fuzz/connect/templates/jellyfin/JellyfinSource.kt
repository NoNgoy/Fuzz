package dev.fuzz.connect.templates.jellyfin

import dev.fuzz.connect.templates.AuthException
import dev.fuzz.connect.templates.flatMap
import dev.fuzz.connect.templates.map
import dev.fuzz.connect.templates.withAuthRetry
import dev.fuzz.core.domain.plugin.ExternalId
import dev.fuzz.core.domain.plugin.MusicSource
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.Providers
import dev.fuzz.core.domain.plugin.SearchResult
import dev.fuzz.core.domain.plugin.SourceCapability
import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.core.network.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * Сервер Jellyfin (и Emby с тем же API): поиск по аудио пользователя и прямой поток файла.
 * Вход по логину и паролю (`/Users/AuthenticateByName`), токен держится в памяти и обновляется
 * при 401. Пароль приходит из Android Keystore. Клиенту нужен ContentNegotiation с JSON
 * (ignoreUnknownKeys = true). [deviceId] должен быть постоянным для установки: по нему Jellyfin
 * показывает устройство в списке сессий.
 */
class JellyfinSource(
    private val client: HttpClient,
    private val baseUrl: String,
    private val username: String,
    private val password: String,
    private val deviceId: String,
    override val id: String = "jellyfin",
    override val displayName: String = "Jellyfin",
) : MusicSource {
    override val capabilities = setOf(SourceCapability.STREAM, SourceCapability.OFFLINE_CACHE)

    private val mutex = Mutex()
    private var session: Session? = null

    override suspend fun search(query: String, limit: Int): PluginResult<SearchResult> =
        withSession { s ->
            val response =
                client.get(url("Users", s.userId, "Items")) {
                    header(HttpHeaders.Authorization, authHeader(s.token))
                    parameter("searchTerm", query)
                    parameter("IncludeItemTypes", "Audio")
                    parameter("Recursive", true)
                    parameter("Limit", limit)
                }
            check(response.status)
            response.body<ItemsResponse>()
        }.map { r -> SearchResult(tracks = r.items.map { it.toRef() }) }

    /** Ссылка на исходный файл с токеном в `api_key`: ExoPlayer не умеет свои заголовки на редиректах. */
    override suspend fun playbackUri(track: TrackRef): PluginResult<String> {
        val itemId =
            track.externalIds.firstOrNull { it.provider == Providers.JELLYFIN }?.id
                ?: return PluginResult.Failure(PluginResult.Kind.NOT_FOUND, "no Jellyfin id")
        return session().map { s ->
            URLBuilder(url("Audio", itemId, "stream")).apply {
                parameters.append("static", "true")
                parameters.append("api_key", s.token)
            }.buildString()
        }
    }

    private suspend fun <T> withSession(block: suspend (Session) -> T): PluginResult<T> =
        withAuthRetry(::session, ::invalidate, block)

    private suspend fun session(): PluginResult<Session> =
        mutex.withLock {
            session?.let { return@withLock PluginResult.Ok(it) }
            val result =
                safeCall {
                    val response =
                        client.post(url("Users", "AuthenticateByName")) {
                            header(HttpHeaders.Authorization, authHeader(token = null))
                            contentType(ContentType.Application.Json)
                            setBody(AuthRequest(username, password))
                        }
                    if (response.status == HttpStatusCode.Unauthorized) return@safeCall null
                    if (response.status !=
                        HttpStatusCode.OK
                    ) {
                        throw IOException("Jellyfin HTTP ${response.status.value}")
                    }
                    response.body<AuthResponse>().let { Session(it.accessToken, it.user.id) }
                }
            result.flatMap { s ->
                s?.let { PluginResult.Ok(it).also { session = s } }
                    ?: PluginResult.Failure(PluginResult.Kind.UNAUTHORIZED, "Wrong username or password")
            }
        }

    private suspend fun invalidate(stale: Session) =
        mutex.withLock { if (session == stale) session = null }

    private fun check(status: HttpStatusCode) {
        if (status == HttpStatusCode.Unauthorized) throw AuthException("Jellyfin token rejected")
        if (status != HttpStatusCode.OK) throw IOException("Jellyfin HTTP ${status.value}")
    }

    private fun url(vararg segments: String): String =
        URLBuilder(baseUrl.trimEnd('/')).apply { appendPathSegments(*segments) }.buildString()

    private fun authHeader(token: String?): String =
        buildString {
            append("MediaBrowser Client=\"$CLIENT_NAME\", Device=\"Android\", ")
            append("DeviceId=\"$deviceId\", Version=\"$CLIENT_VERSION\"")
            if (token != null) append(", Token=\"$token\"")
        }

    private fun ItemDto.toRef() =
        TrackRef(
            title = name,
            artist = artists.firstOrNull() ?: albumArtist.orEmpty(),
            album = album,
            durationMs = runTimeTicks?.div(TICKS_PER_MS),
            externalIds = listOf(ExternalId(Providers.JELLYFIN, id)),
        )

    private data class Session(val token: String, val userId: String)

    private companion object {
        const val CLIENT_NAME = "Fuzz"
        const val CLIENT_VERSION = "1.0"
        const val TICKS_PER_MS = 10_000L
    }
}
