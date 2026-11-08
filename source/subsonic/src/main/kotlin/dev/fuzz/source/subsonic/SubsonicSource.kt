package dev.fuzz.source.subsonic

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
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Любой Subsonic/OpenSubsonic-сервер (Navidrome, Gonic, Airsonic): поиск и потоковые ссылки.
 * Авторизация токеном (md5(пароль + соль)), пароль по сети не передаётся. Пароль хранится снаружи
 * (Android Keystore) и передаётся в конструктор. Клиент должен иметь ContentNegotiation с JSON
 * (ignoreUnknownKeys = true); Subsonic отвечает `application/json` только при `f=json`.
 */
class SubsonicSource(
    private val client: HttpClient,
    private val baseUrl: String,
    private val username: String,
    private val password: String,
    override val id: String = "subsonic",
    override val displayName: String = "Subsonic",
    private val saltProvider: () -> String = ::randomSalt,
) : MusicSource {
    override val capabilities = setOf(SourceCapability.STREAM, SourceCapability.OFFLINE_CACHE)

    override suspend fun search(query: String, limit: Int): PluginResult<SearchResult> =
        call("search3") {
            parameter("query", query)
            parameter("songCount", limit)
            parameter("artistCount", 0)
            parameter("albumCount", 0)
        }.map { SearchResult(tracks = it.searchResult3?.song.orEmpty().map { song -> song.toRef() }) }

    /** Ссылка на поток со свежей солью. Токен действует, пока пароль не сменили; ссылку не сохранять. */
    override suspend fun playbackUri(track: TrackRef): PluginResult<String> {
        val songId =
            track.externalIds.firstOrNull { it.provider == Providers.SUBSONIC }?.id
                ?: return PluginResult.Failure(PluginResult.Kind.NOT_FOUND, "no Subsonic id")
        return PluginResult.Ok(url("stream") { parameters.append("id", songId) })
    }

    private suspend fun call(
        method: String,
        block: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
    ): PluginResult<SubsonicResponse> {
        val result =
            safeCall {
                val http = client.get(url(method) {}) { block() }
                if (http.status != HttpStatusCode.OK) throw IOException("Subsonic HTTP ${http.status.value}")
                http.body<SubsonicEnvelope>().response
            }
        if (result !is PluginResult.Ok) return result
        val response = result.value
        if (response.status == "ok") return result
        val kind =
            if (response.error?.code in AUTH_ERROR_CODES) {
                PluginResult.Kind.UNAUTHORIZED
            } else {
                PluginResult.Kind.NETWORK
            }
        return PluginResult.Failure(kind, response.error?.message)
    }

    private inline fun <T, R> PluginResult<T>.map(transform: (T) -> R): PluginResult<R> =
        when (this) {
            is PluginResult.Ok -> PluginResult.Ok(transform(value))
            is PluginResult.Failure -> this
        }

    private fun url(method: String, extra: URLBuilder.() -> Unit): String {
        val salt = saltProvider()
        // Токен считается вне URLBuilder: у него свои свойства user/password, они затеняют наши.
        val token = md5(password + salt)
        return URLBuilder(baseUrl.trimEnd('/')).apply {
            appendPathSegments("rest", "$method.view")
            parameters.append("u", username)
            parameters.append("t", token)
            parameters.append("s", salt)
            parameters.append("v", API_VERSION)
            parameters.append("c", CLIENT_NAME)
            parameters.append("f", "json")
            extra()
        }.buildString()
    }

    private fun SongDto.toRef() =
        TrackRef(
            title = title,
            artist = artist,
            album = album,
            durationMs = duration?.times(MS_IN_SECOND),
            externalIds = listOf(ExternalId(Providers.SUBSONIC, id)),
        )

    private companion object {
        const val API_VERSION = "1.16.1"
        const val CLIENT_NAME = "Fuzz"
        const val MS_IN_SECOND = 1_000L
        val AUTH_ERROR_CODES = setOf(40, 41, 42, 43, 44)
    }
}

private const val SALT_BYTES = 6

private fun randomSalt(): String {
    val bytes = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
    return bytes.joinToString("") { "%02x".format(it) }
}

private fun md5(text: String): String =
    MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
