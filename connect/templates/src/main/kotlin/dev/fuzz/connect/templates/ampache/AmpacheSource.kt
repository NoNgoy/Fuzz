package dev.fuzz.connect.templates.ampache

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
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.security.MessageDigest

/** Как войти в Ampache: логин и пароль (пароль по сети не идёт) или API-ключ из профиля. */
sealed interface AmpacheCredentials {
    data class Password(val username: String, val password: String) : AmpacheCredentials

    data class ApiKey(val key: String) : AmpacheCredentials
}

/**
 * Сервер Ampache через JSON API (`/server/json.server.php`): поиск песен и ссылка на поток.
 * Сессия берётся рукопожатием (`handshake`) и обновляется, когда сервер отвечает «сессия истекла».
 * Клиенту нужен ContentNegotiation с JSON (ignoreUnknownKeys = true).
 */
class AmpacheSource(
    private val client: HttpClient,
    private val baseUrl: String,
    private val credentials: AmpacheCredentials,
    override val id: String = "ampache",
    override val displayName: String = "Ampache",
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / MS_IN_SECOND },
) : MusicSource {
    override val capabilities = setOf(SourceCapability.STREAM, SourceCapability.OFFLINE_CACHE)

    private val mutex = Mutex()
    private var auth: String? = null

    override suspend fun search(query: String, limit: Int): PluginResult<SearchResult> =
        authorized { token ->
            val response =
                client.get(
                    api("search_songs", token) {
                        parameters.append("filter", query)
                        parameters.append("limit", limit.toString())
                    },
                )
            if (response.status != HttpStatusCode.OK) throw IOException("Ampache HTTP ${response.status.value}")
            val body = response.body<SongsResponse>()
            body.error?.let { raise(it) }
            body
        }.map { r -> SearchResult(tracks = r.song.map { it.toRef() }) }

    /** Ссылка `action=stream` с токеном сессии: живёт, пока жива сессия, поэтому её не сохраняют. */
    override suspend fun playbackUri(track: TrackRef): PluginResult<String> {
        val songId =
            track.externalIds.firstOrNull { it.provider == Providers.AMPACHE }?.id
                ?: return PluginResult.Failure(PluginResult.Kind.NOT_FOUND, "no Ampache id")
        return session().map { token ->
            api("stream", token) {
                parameters.append("type", "song")
                parameters.append("id", songId)
            }
        }
    }

    private suspend fun <T> authorized(block: suspend (String) -> T): PluginResult<T> =
        withAuthRetry(::session, ::invalidate, block)

    private suspend fun session(): PluginResult<String> =
        mutex.withLock {
            auth?.let { return@withLock PluginResult.Ok(it) }
            safeCall {
                val response = client.get(handshakeUrl())
                if (response.status != HttpStatusCode.OK) throw IOException("Ampache HTTP ${response.status.value}")
                response.body<HandshakeResponse>()
            }.flatMap { r ->
                when {
                    r.auth != null -> PluginResult.Ok(r.auth).also { auth = r.auth }
                    else -> PluginResult.Failure(PluginResult.Kind.UNAUTHORIZED, r.error?.errorMessage)
                }
            }
        }

    private suspend fun invalidate(stale: String) = mutex.withLock { if (auth == stale) auth = null }

    private fun raise(error: AmpacheError): Nothing {
        val code = (error.errorCode as? JsonPrimitive)?.content?.toIntOrNull()
        if (code in AUTH_ERROR_CODES) throw AuthException(error.errorMessage ?: "Ampache session rejected")
        throw IOException("Ampache error $code: ${error.errorMessage}")
    }

    private fun handshakeUrl(): String =
        when (val c = credentials) {
            is AmpacheCredentials.ApiKey -> {
                api("handshake", c.key) {}
            }

            is AmpacheCredentials.Password -> {
                val timestamp = nowSeconds().toString()
                // Пароль по сети не передаётся: auth = sha256(timestamp + sha256(password)).
                api("handshake", sha256(timestamp + sha256(c.password))) {
                    parameters.append("timestamp", timestamp)
                    parameters.append("user", c.username)
                }
            }
        }

    private fun api(action: String, auth: String, extra: URLBuilder.() -> Unit): String =
        URLBuilder(baseUrl.trimEnd('/')).apply {
            appendPathSegments("server", "json.server.php")
            parameters.append("action", action)
            parameters.append("auth", auth)
            parameters.append("version", API_VERSION)
            extra()
        }.buildString()

    private fun SongDto.toRef() =
        TrackRef(
            title = title,
            artist = artist?.name.orEmpty(),
            album = album?.name,
            durationMs = time?.times(MS_IN_SECOND),
            externalIds = listOf(ExternalId(Providers.AMPACHE, id)),
        )

    private companion object {
        const val API_VERSION = "6.0.0"
        const val MS_IN_SECOND = 1_000L

        /** 4701 сессия истекла, 4703 нет доступа к методу из-за сессии, 4742 рукопожатие отклонено. */
        val AUTH_ERROR_CODES = setOf(4701, 4703, 4742)
    }
}

private fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
