package dev.fuzz.engine.recommend.profile

import dev.fuzz.core.model.Reaction
import dev.fuzz.core.model.TrackId
import dev.fuzz.engine.recommend.config.ProfileConfig
import dev.fuzz.engine.recommend.model.AudioFeatures
import dev.fuzz.engine.recommend.model.WaveSignal
import dev.fuzz.engine.recommend.model.WaveTrack
import kotlin.math.pow

/**
 * Копит профиль вкуса из событий с затуханием (полупериод из конфига). Вызывается и для полной
 * ночной пересборки по всей истории, и инкрементно после каждого события: [add] дешёвый.
 *
 * [lookup] даёт признаки трека по ID; события по трекам, которых больше нет, учитываются только в статистике.
 */
class TasteProfileBuilder(
    private val config: ProfileConfig,
    private val nowMs: Long,
    private val lookup: (TrackId) -> WaveTrack?,
) {
    private val tags = HashMap<String, Float>()
    private val artists = HashMap<String, Float>()
    private val stats = HashMap<TrackId, TrackStats>()
    private var audioSum = FloatArray(AUDIO_DIMENSIONS)
    private var audioWeight = 0f

    /** Продолжить копить поверх готового снимка: так сессия волны обновляет вкус после каждого события. */
    fun startFrom(profile: TasteProfile): TasteProfileBuilder {
        tags.putAll(profile.tags)
        artists.putAll(profile.artists)
        stats.putAll(profile.tracks)
        profile.audio?.let {
            audioWeight = SNAPSHOT_AUDIO_WEIGHT
            audioSum = floatArrayOf(it.bpm * audioWeight, it.energy * audioWeight, it.valence * audioWeight)
        }
        return this
    }

    /** Холодный старт: всё, что человек сам положил в коллекцию, — слабый лайк. */
    fun addCollectionPrior(collection: Collection<WaveTrack>): TasteProfileBuilder {
        collection.forEach { applyToVectors(it, config.collectionPrior) }
        return this
    }

    fun addAll(signals: Iterable<WaveSignal>): TasteProfileBuilder {
        signals.sortedBy { it.atMs }.forEach { add(it) }
        return this
    }

    fun add(
        signal: WaveSignal,
        boost: Float = 1f,
    ): TasteProfileBuilder {
        val decay = decay(signal.atMs)
        val previous = stats[signal.trackId] ?: TrackStats()
        val (weight, updated) = weigh(signal, previous)
        val effective = weight * decay * boost
        stats[signal.trackId] = updated.copy(affinity = updated.affinity + effective)
        lookup(signal.trackId)?.let { applyToVectors(it, effective) }
        return this
    }

    fun build(): TasteProfile =
        TasteProfile(
            tags = HashMap(tags),
            artists = HashMap(artists),
            audio = if (audioWeight > 0f) audioMean() else null,
            tracks = HashMap(stats),
        )

    private fun weigh(
        signal: WaveSignal,
        s: TrackStats,
    ): Pair<Float, TrackStats> =
        when (signal) {
            is WaveSignal.Listen -> weighListen(signal, s)
            is WaveSignal.Like -> config.like to s.copy(reaction = Reaction.Like)
            is WaveSignal.Dislike -> config.dislike to s.copy(reaction = Reaction.Dislike)
            is WaveSignal.AddedToPlaylist -> config.addedToPlaylist to s.copy(playlistAdds = s.playlistAdds + 1)
            is WaveSignal.ClearReaction -> clearReaction(s)
        }

    private fun weighListen(
        signal: WaveSignal.Listen,
        s: TrackStats,
    ): Pair<Float, TrackStats> {
        val played = s.copy(plays = s.plays + 1, lastPlayedAtMs = maxOf(s.lastPlayedAtMs ?: 0, signal.atMs))
        return when {
            signal.completed -> {
                config.completed to played.copy(completed = s.completed + 1)
            }

            signal.playedMs < config.earlySkipMs -> {
                config.earlySkip to played.copy(skips = s.skips + 1, earlySkips = s.earlySkips + 1)
            }

            else -> {
                config.lateSkip to played.copy(skips = s.skips + 1)
            }
        }
    }

    /** Снятие реакции откатывает её вклад, чтобы профиль не помнил отменённый лайк. */
    private fun clearReaction(s: TrackStats): Pair<Float, TrackStats> {
        val undo =
            when (s.reaction) {
                Reaction.Like -> -config.like
                Reaction.Dislike -> -config.dislike
                Reaction.None -> 0f
            }
        return undo to s.copy(reaction = Reaction.None)
    }

    private fun applyToVectors(
        track: WaveTrack,
        weight: Float,
    ) {
        if (weight == 0f) return
        artists.merge(track.artistKey, weight, Float::plus)
        track.tags.forEach { (tag, w) -> tags.merge(tag, weight * w, Float::plus) }
        val audio = track.audio
        if (audio != null && weight > 0f) {
            audioSum[0] += audio.bpm * weight
            audioSum[1] += audio.energy * weight
            audioSum[2] += audio.valence * weight
            audioWeight += weight
        }
    }

    private fun audioMean() =
        AudioFeatures(
            bpm = audioSum[0] / audioWeight,
            energy = audioSum[1] / audioWeight,
            valence = audioSum[2] / audioWeight,
        )

    private fun decay(atMs: Long): Float {
        val ageDays = (nowMs - atMs).coerceAtLeast(0) / MS_PER_DAY
        return 0.5.pow(ageDays / config.halfLifeDays).toFloat()
    }

    private companion object {
        const val AUDIO_DIMENSIONS = 3

        /** Сколько «весят» средние аудиопризнаки снимка против новых событий. */
        const val SNAPSHOT_AUDIO_WEIGHT = 20f
        const val MS_PER_DAY = 86_400_000.0
    }
}
