package dev.fuzz.connect.manifest

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.Base64

/** Токен не выдан: неверные client_id/секрет или отозванный refresh token. */
internal class OAuthRejected(message: String) : IOException(message)

/**
 * Токены OAuth 2 для манифеста: client credentials или refresh token. Access token держится в памяти
 * до истечения (с запасом [SKEW_MS]), [invalidate] сбрасывает его после 401. Если сервер выдал новый
 * refresh token, он передаётся в [onRefreshTokenRotated], чтобы приложение сохранило его в Keystore.
 */
internal class OAuth2Tokens(
    private val client: HttpClient,
    private val auth: AuthSpec,
    secret: String,
    private val onRefreshTokenRotated: (String) -> Unit,
    private val now: () -> Long,
) {
    private val clientId: String
    private val clientSecret: String
    private var refreshToken: String?

    init {
        val parts = secret.split(':', limit = 3)
        clientId = parts[0]
        clientSecret = parts.getOrElse(1) { "" }
        refreshToken = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }
    }

    private val mutex = Mutex()
    private var token: String? = null
    private var expiresAt = 0L

    suspend fun accessToken(): String =
        mutex.withLock {
            token?.takeIf { now() < expiresAt }?.let { return@withLock it }
            fetch()
        }

    suspend fun invalidate(stale: String) = mutex.withLock { if (token == stale) token = null }

    private suspend fun fetch(): String {
        val response =
            client.submitForm(
                url = requireNotNull(auth.tokenUrl),
                formParameters =
                    parameters {
                        append("grant_type", auth.grant)
                        if (auth.grant == "refresh_token") append("refresh_token", refreshToken.orEmpty())
                        auth.scope?.let { append("scope", it) }
                        if (clientSecret.isEmpty()) append("client_id", clientId)
                    },
            ) {
                if (clientSecret.isNotEmpty()) {
                    val basic = Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray())
                    header(HttpHeaders.Authorization, "Basic $basic")
                }
            }
        if (response.status == HttpStatusCode.BadRequest || response.status == HttpStatusCode.Unauthorized) {
            throw OAuthRejected("Токен не выдан: HTTP ${response.status.value}")
        }
        if (response.status != HttpStatusCode.OK) throw IOException("OAuth HTTP ${response.status.value}")
        return remember(decode(response.bodyAsText()))
    }

    private fun decode(text: String): TokenResponse =
        try {
            json.decodeFromString<TokenResponse>(text)
        } catch (e: SerializationException) {
            throw IOException("OAuth: ответ не JSON", e)
        }

    private fun remember(body: TokenResponse): String {
        body.refreshToken?.takeIf { it != refreshToken }?.let {
            refreshToken = it
            onRefreshTokenRotated(it)
        }
        token = body.accessToken
        expiresAt = now() + (body.expiresIn ?: DEFAULT_LIFETIME_S) * MS_IN_SECOND - SKEW_MS
        return body.accessToken
    }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Long? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
    )

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        const val DEFAULT_LIFETIME_S = 3_600L
        const val MS_IN_SECOND = 1_000L
        const val SKEW_MS = 30_000L
    }
}
