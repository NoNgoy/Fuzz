package dev.fuzz.core.data.wave

import dev.fuzz.core.data.sync.Clock
import dev.fuzz.core.database.dao.ListenDao
import dev.fuzz.core.database.entity.ListenEntity
import dev.fuzz.core.domain.player.PlayerController
import dev.fuzz.core.domain.wave.Listen
import dev.fuzz.core.model.PlaybackState
import dev.fuzz.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Пишет историю прослушиваний из состояния плеера: когда играющий трек сменился, предыдущий записывается
 * с тем, сколько из него прозвучало. Дослушанным считается трек, от которого осталось меньше [TAIL_MS]
 * или меньше 10%. Каждое прослушивание также уходит в [listens] для текущей сессии волны.
 */
@Singleton
class ListenRecorder
    @Inject
    internal constructor(
        private val player: PlayerController,
        private val dao: ListenDao,
        private val clock: Clock,
    ) {
        private val events = MutableSharedFlow<Listen>(extraBufferCapacity = BUFFER)
        val listens: SharedFlow<Listen> = events.asSharedFlow()

        private var current: Track? = null
        private var startedAt = 0L
        private var lastPositionMs = 0L
        private var started = false

        fun start(scope: CoroutineScope) {
            if (started) return
            started = true
            scope.launch {
                dao.deleteOlderThan(clock.nowMs() - HISTORY_MS)
                player.state.collect { onState(it) }
            }
        }

        internal suspend fun onState(state: PlaybackState) {
            val track = state.currentTrack
            if (track?.id != current?.id) {
                current?.let { finish(it, lastPositionMs) }
                current = track
                startedAt = clock.nowMs()
            }
            lastPositionMs = state.positionMs
        }

        private suspend fun finish(
            track: Track,
            playedMs: Long,
        ) {
            if (playedMs <= 0) return
            val duration = track.durationMs
            val completed =
                duration > 0 && (duration - playedMs <= TAIL_MS || playedMs >= duration * COMPLETE_SHARE)
            val listen = Listen(track.id, startedAt, playedMs, completed)
            dao.insert(
                ListenEntity(
                    trackId = track.id.value,
                    startedAt = startedAt,
                    playedMs = playedMs,
                    completed = completed,
                ),
            )
            events.emit(listen)
        }

        internal companion object {
            const val TAIL_MS = 5_000L
            const val COMPLETE_SHARE = 0.9
            const val BUFFER = 16

            /** Сколько истории держать: вклад в профиль затухает за 30 дней, полгода с запасом. */
            const val HISTORY_MS = 180L * 24 * 60 * 60 * 1000
        }
    }
