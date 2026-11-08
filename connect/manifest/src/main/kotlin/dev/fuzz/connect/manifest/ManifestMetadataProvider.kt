package dev.fuzz.connect.manifest

import dev.fuzz.core.domain.plugin.AlbumRef
import dev.fuzz.core.domain.plugin.ArtistRef
import dev.fuzz.core.domain.plugin.ExternalId
import dev.fuzz.core.domain.plugin.MetadataProvider
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.Similar
import dev.fuzz.core.domain.plugin.Tag
import dev.fuzz.core.domain.plugin.TrackRef
import kotlinx.serialization.json.JsonElement

/**
 * Похожие треки и артисты из своего API по [SimilarSpec], чтобы такой API мог кормить волну
 * (подключается в граф похожести как любой провайдер метаданных). Теги, дискографии и обложки
 * манифест не описывает: они отвечают NOT_FOUND.
 */
class ManifestMetadataProvider internal constructor(
    manifest: Manifest,
    private val spec: SimilarSpec,
    private val http: ManifestHttp,
) : MetadataProvider {
    override val id = manifest.id
    override val displayName = manifest.name
    private val provider = manifest.id

    override suspend fun similarArtists(artist: ArtistRef, limit: Int): PluginResult<List<Similar<ArtistRef>>> {
        val q = spec.artists ?: return notDescribed()
        val vars = mapOf("artist" to artist.name, "title" to "", "id" to idOf(artist.externalIds), "limit" to "$limit")
        return fetch(q, vars, limit) { item ->
            JsonPathLite.text(item, q.artist)?.let { name -> ArtistRef(name, externalIds(q, item)) }
        }
    }

    override suspend fun similarTracks(track: TrackRef, limit: Int): PluginResult<List<Similar<TrackRef>>> {
        val q = spec.tracks ?: return notDescribed()
        val vars = http.trackVars(track) + ("limit" to "$limit")
        return fetch(q, vars, limit) { item ->
            val title = q.title?.let { JsonPathLite.text(item, it) } ?: return@fetch null
            TrackRef(title, JsonPathLite.text(item, q.artist).orEmpty(), externalIds = externalIds(q, item))
        }
    }

    override suspend fun tags(track: TrackRef): PluginResult<List<Tag>> = notDescribed()

    override suspend fun discography(artist: ArtistRef): PluginResult<List<AlbumRef>> = notDescribed()

    override suspend fun coverUrl(album: AlbumRef): PluginResult<String> = notDescribed()

    private suspend fun <T : Any> fetch(
        q: SimilarQuery,
        vars: Map<String, String>,
        limit: Int,
        toItem: (JsonElement) -> T?,
    ): PluginResult<List<Similar<T>>> {
        val root =
            when (val result = http.getJson(q.path, q.query, vars)) {
                is PluginResult.Failure -> return result
                is PluginResult.Ok -> result.value
            }
        val items = JsonPathLite.select(root, q.results).take(limit)
        val similar =
            items.mapIndexedNotNull { index, item ->
                toItem(item)?.let { Similar(it, score(q, item, index, items.size)) }
            }
        return PluginResult.Ok(similar)
    }

    /** Близость 0..1: из ответа или по месту в списке (первый 1.0, дальше линейно вниз). */
    private fun score(q: SimilarQuery, item: JsonElement, index: Int, count: Int): Float {
        val fromApi = q.score?.let { JsonPathLite.text(item, it) }?.toDoubleOrNull()?.div(q.scoreScale)
        val value = fromApi ?: (1.0 - index.toDouble() / count.coerceAtLeast(1))
        return value.coerceIn(0.0, 1.0).toFloat()
    }

    private fun externalIds(q: SimilarQuery, item: JsonElement): List<ExternalId> =
        q.id?.let { JsonPathLite.text(item, it) }?.let { listOf(ExternalId(provider, it)) }.orEmpty()

    private fun idOf(ids: List<ExternalId>) = ids.firstOrNull { it.provider == provider }?.id.orEmpty()

    private fun notDescribed() = PluginResult.Failure(PluginResult.Kind.NOT_FOUND, "not described in manifest")
}
