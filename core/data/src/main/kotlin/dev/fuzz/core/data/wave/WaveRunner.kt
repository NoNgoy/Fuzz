package dev.fuzz.core.data.wave

import dev.fuzz.core.data.sync.Clock
import dev.fuzz.core.domain.player.PlayerController
import dev.fuzz.core.domain.reaction.ReactionRepository
import dev.fuzz.core.domain.wave.WavePlayer
import dev.fuzz.core.domain.wave.WaveSettingsRepository
import dev.fuzz.core.domain.wave.WaveShare
import dev.fuzz.core.domain.wave.WaveSource
import dev.fuzz.core.domain.wave.WaveStartResult
import dev.fuzz.core.domain.wave.WaveStatus
import dev.fuzz.core.model.PlaybackState
import dev.fuzz.core.model.Reaction
import dev.fuzz.core.model.TrackId
import dev.fuzz.core.model.wave.WaveSeed
import dev.fuzz.engine.recommend.WaveEngine
import dev.fuzz.engine.recommend.WaveSession
import dev.fuzz.engine.recommend.config.WaveConfig
import dev.fuzz.engine.recommend.model.Bucket
import dev.fuzz.engine.recommend.model.TrackOrigin
import dev.fuzz.engine.recommend.model.WavePick
import dev.fuzz.engine.recommend.model.WaveSignal
import dev.fuzz.engine.recommend.port.ExternalCatalog
import dev.fuzz.engine.recommend.port.WaveLibrary
import dev.fuzz.engine.recommend.rank.BucketMix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Связывает сессию движка волны с плеером. Первая пачка собирается из локальных корзин и сразу играет,
 * сетевые корзины доливаются фоном. Когда впереди остаётся [REFILL_AT] трека, просится следующая пачка.
 * Пропуск, лайк, дизлайк и смена настроек пересобирают несыгранный остаток очереди. Если пользователь
 * включил что-то не из волны, волна молча отпускает плеер.
 */
@Singleton
class WaveRunner
    @Inject
    internal constructor(
        private val engine: WaveEngine,
        private val library: WaveLibrary,
        private val catalogs: List<@JvmSuppressWildcards ExternalCatalog>,
        private val data: WaveTracks,
        private val player: PlayerController,
        private val settings: WaveSettingsRepository,
        private val recorder: ListenRecorder,
        private val reactionRepository: ReactionRepository,
        private val clock: Clock,
    ) : WavePlayer {
        private val mutableStatus = MutableStateFlow(WaveStatus())
        override val status: StateFlow<WaveStatus> = mutableStatus.asStateFlow()

        private var scope: CoroutineScope? = null
        private var job: Job? = null

        /** Треки, которые волна положила в очередь. Только их сигналы идут в сессию. */
        private val issued: MutableSet<TrackId> = java.util.concurrent.ConcurrentHashMap.newKeySet()

        fun attach(scope: CoroutineScope) {
            this.scope = scope
        }

        override suspend fun start(seed: WaveSeed): WaveStartResult {
            val scope = checkNotNull(scope) { "WaveRunner.attach не вызван" }
            mutableStatus.value = WaveStatus(starting = true, seed = seed)
            job?.cancelAndJoin()
            val collection = library.tracks()
            if (collection.isEmpty()) {
                mutableStatus.value = WaveStatus()
                return WaveStartResult.EmptyLibrary
            }
            val session =
                engine.session(
                    seed,
                    settings.settings.first(),
                    engine.buildProfile(collection, data.history()),
                )
            val first = data.resolve(session.start())
            if (first.isEmpty()) {
                mutableStatus.value = WaveStatus()
                return WaveStartResult.EmptyLibrary
            }
            issued.clear()
            issued += first.map { it.id }
            player.playQueue(first)
            mutableStatus.value = WaveStatus(active = true, seed = seed)
            job = scope.launch { follow(session) }
            return WaveStartResult.Started
        }

        override fun mix(familiarity: Int): List<WaveShare> {
            val available =
                buildSet {
                    add(Bucket.FAVORITES)
                    add(Bucket.UNHEARD)
                    add(Bucket.SIMILAR)
                    catalogs.forEach {
                        add(
                            if (it.origin ==
                                TrackOrigin.Preview
                            ) {
                                Bucket.PREVIEW
                            } else {
                                Bucket.OPEN_CATALOG
                            },
                        )
                    }
                }
            val shares = BucketMix.redistribute(BucketMix.shares(WaveConfig.Default.mix, familiarity), available)
            return WaveSource.entries.mapNotNull { source -> shares[source.bucket]?.let { WaveShare(source, it) } }
        }

        private suspend fun follow(session: WaveSession) =
            coroutineScope {
                val follower = Follower(session)
                launch { runCatching { session.enrich() } }
                launch { player.state.collect { follower.onPlayback(it) } }
                launch {
                    recorder.listens.collect {
                        follower.onSignal(WaveSignal.Listen(it.trackId, it.startedAtMs, it.playedMs, it.completed))
                    }
                }
                launch { reactionSignals().collect { follower.onSignal(it) } }
                launch {
                    settings.settings.drop(1).collect { new ->
                        val state = player.state.value
                        follower.replaceUpcoming(session.updateSettings(new, state.upcoming()), state)
                    }
                }
            }

        private inner class Follower(
            private val session: WaveSession,
        ) {
            /** Плеер уже показал трек из волны: до этого старая очередь ещё может мелькать в состоянии. */
            private var joined = false
            private var refilledAtSize = -1

            suspend fun onPlayback(state: PlaybackState) {
                val id = state.currentTrack?.id ?: return
                if (id !in issued) {
                    if (joined) release()
                    return
                }
                joined = true
                val remaining = state.queue.size - state.currentIndex - 1
                if (remaining <= REFILL_AT && state.queue.size != refilledAtSize) {
                    refilledAtSize = state.queue.size
                    val more = data.resolve(session.nextBatch())
                    issued += more.map { it.id }
                    player.addToQueue(more)
                }
            }

            suspend fun onSignal(signal: WaveSignal) {
                if (signal.trackId !in issued) return
                val state = player.state.value
                val upcoming = state.upcoming()
                val picks = session.onSignal(signal, upcoming)
                if (picks.map { it.track.id } != upcoming) replaceUpcoming(picks, state)
                if (signal is WaveSignal.Dislike && state.currentTrack?.id == signal.trackId) player.skipToNext()
            }

            suspend fun replaceUpcoming(
                picks: List<WavePick>,
                state: PlaybackState,
            ) {
                val fresh = data.resolve(picks)
                for (index in state.queue.lastIndex downTo state.currentIndex + 1) player.removeFromQueue(index)
                issued += fresh.map { it.id }
                player.addToQueue(fresh)
                refilledAtSize = -1
            }

            private fun release() {
                mutableStatus.value = WaveStatus()
                job?.cancel()
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun reactionSignals(): Flow<WaveSignal> =
            player.state
                .map { it.currentTrack?.id }
                .distinctUntilChanged()
                .flatMapLatest { id ->
                    if (id == null) {
                        emptyFlow()
                    } else {
                        reactionRepository.observe(id).drop(1).map { reaction ->
                            val now = clock.nowMs()
                            when (reaction) {
                                Reaction.Like -> WaveSignal.Like(id, now)
                                Reaction.Dislike -> WaveSignal.Dislike(id, now)
                                Reaction.None -> WaveSignal.ClearReaction(id, now)
                            }
                        }
                    }
                }

        private fun PlaybackState.upcoming(): List<TrackId> = queue.drop(currentIndex + 1).map { it.id }

        private val WaveSource.bucket: Bucket
            get() =
                when (this) {
                    WaveSource.Favorites -> Bucket.FAVORITES
                    WaveSource.Unheard -> Bucket.UNHEARD
                    WaveSource.Similar -> Bucket.SIMILAR
                    WaveSource.OpenCatalog -> Bucket.OPEN_CATALOG
                    WaveSource.Preview -> Bucket.PREVIEW
                }

        private companion object {
            const val REFILL_AT = 3
        }
    }
