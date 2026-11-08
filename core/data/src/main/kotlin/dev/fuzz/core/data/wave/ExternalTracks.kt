package dev.fuzz.core.data.wave

import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.core.model.TrackId
import dev.fuzz.engine.recommend.model.TrackOrigin
import dev.fuzz.engine.recommend.model.WaveTrack
import java.util.UUID

/**
 * Трек вне коллекции для движка волны. ID детерминирован (из провайдера и внешнего ID), поэтому один и тот же
 * трек получает один ID между запусками и не плодит дубли в пуле. Без внешнего ID трек отбрасывается.
 */
internal fun TrackRef.toWaveTrack(
    provider: String,
    origin: TrackOrigin,
    tags: Map<String, Float> = emptyMap(),
): WaveTrack? {
    val externalId = externalIds.firstOrNull { it.provider == provider }?.id ?: return null
    return WaveTrack(
        id = TrackId(UUID.nameUUIDFromBytes("$provider:$externalId".toByteArray()).toString()),
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs ?: 0L,
        origin = origin,
        tags = tags,
    )
}
