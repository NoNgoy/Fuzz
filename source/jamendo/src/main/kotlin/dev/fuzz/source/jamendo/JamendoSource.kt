package dev.fuzz.source.jamendo

import dev.fuzz.core.domain.plugin.ExternalId
import dev.fuzz.core.domain.plugin.MusicSource
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.Providers
import dev.fuzz.core.domain.plugin.SearchResult
import dev.fuzz.core.domain.plugin.SourceCapability
import dev.fuzz.core.domain.plugin.TagSearchable
import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.core.network.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import java.io.IOException

/**
 * Jamendo: музыка под Creative Commons, стриминг и скачивание.
 * [clientId] — бесплатный ключ для некоммерческого использования, в репозиторий не коммитится.
 * Клиент должен иметь ContentNegotiation с JSON (ignoreUnknownKeys = true).
 */
class JamendoSource(
    private val client: HttpClient,
    private val clientId: String,
    private val baseUrl: String = "https://api.jamendo.com/v3.0",
) : MusicSource, TagSearchable {
    override val id = "jamendo"
    override val displayName = "Jamendo"
    override val capabilities = setOf(SourceCapability.STREAM, SourceCapability.DOWNLOAD)

    override suspend fun search(query: String, limit: Int): PluginResult<SearchResult> = searchPage(query, limit, 0)

    override suspend fun searchPage(query: String, limit: Int, offset: Int): PluginResult<SearchResult> =
        safeCall {
            SearchResult(
                tracks =
                    fetch {
                        parameter("search", query)
                        parameter("limit", limit)
                        parameter("offset", offset)
                    }.map { it.toRef() },
            )
        }

    /** Треки по тегам Jamendo (`tags=rock+energetic` — пересечение), с пагинацией. */
    override suspend fun byTags(tags: List<String>, limit: Int, offset: Int): PluginResult<SearchResult> =
        safeCall {
            val found =
                fetch {
                    parameter("tags", tags.joinToString("+"))
                    parameter("limit", limit)
                    parameter("offset", offset)
                    parameter("order", "popularity_total")
                }
            SearchResult(tracks = found.map { it.toRef() })
        }

    override suspend fun playbackUri(track: TrackRef): PluginResult<String> {
        val jamendoId =
            track.externalIds.firstOrNull { it.provider == Providers.JAMENDO }?.id
                ?: return PluginResult.Failure(PluginResult.Kind.NOT_FOUND, "no Jamendo id")
        val result =
            safeCall {
                fetch {
                    parameter("id", jamendoId)
                    parameter("limit", 1)
                }.firstOrNull()
            }
        return when (result) {
            is PluginResult.Failure -> {
                result
            }

            is PluginResult.Ok -> {
                result.value?.audio?.let { PluginResult.Ok(it) }
                    ?: PluginResult.Failure(PluginResult.Kind.NOT_FOUND)
            }
        }
    }

    private suspend fun fetch(block: io.ktor.client.request.HttpRequestBuilder.() -> Unit): List<JamendoTrackDto> {
        val response =
            client.get("$baseUrl/tracks/") {
                parameter("client_id", clientId)
                parameter("format", "json")
                parameter("audioformat", "mp32")
                block()
            }
        if (response.status != HttpStatusCode.OK) throw IOException("Jamendo HTTP ${response.status.value}")
        return response.body<JamendoTracksResponse>().results
    }

    private fun JamendoTrackDto.toRef() =
        TrackRef(
            title = name,
            artist = artistName,
            album = albumName,
            durationMs = duration?.times(MS_IN_SECOND),
            externalIds = listOf(ExternalId(Providers.JAMENDO, id)),
        )

    private companion object {
        const val MS_IN_SECOND = 1_000L
    }
}
