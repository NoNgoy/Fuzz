package dev.fuzz.core.data.connection

import dev.fuzz.core.domain.connection.ConnectedSources
import dev.fuzz.core.domain.connection.ConnectionCheck
import dev.fuzz.core.domain.connection.ConnectionConfig
import dev.fuzz.core.domain.connection.ConnectionPlugins
import dev.fuzz.core.domain.connection.ConnectionsRepository
import dev.fuzz.core.domain.connection.MusicSourceFactory
import dev.fuzz.core.domain.plugin.LyricsProvider
import dev.fuzz.core.domain.plugin.MetadataProvider
import dev.fuzz.core.domain.plugin.MusicSource
import dev.fuzz.core.domain.plugin.PluginResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Подключения пользователя. Перед сохранением подключение проверяется пробным поиском, так что
 * в списке оказываются только рабочие. Включённые подключения сразу становятся источниками
 * для поиска и волны ([ConnectedSources]).
 */
@Singleton
internal class DefaultConnectionsRepository(
    private val store: ConnectionsStore,
    private val factory: MusicSourceFactory,
    scope: CoroutineScope,
) : ConnectionsRepository,
    ConnectedSources {
    @Inject
    constructor(store: ConnectionsStore, factory: MusicSourceFactory) :
        this(store, factory, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    override val connections: Flow<List<ConnectionConfig>> = store.connections

    private val plugins: StateFlow<List<ConnectionPlugins>> =
        store.connections
            .map { list -> list.filter { it.enabled }.mapNotNull { build(it) } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val sources: StateFlow<List<MusicSource>> =
        plugins.map { list -> list.map { it.source } }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val lyrics: StateFlow<List<LyricsProvider>> =
        plugins.map { list -> list.mapNotNull { it.lyrics } }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val metadata: StateFlow<List<MetadataProvider>> =
        plugins.map { list -> list.mapNotNull { it.metadata } }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun save(
        config: ConnectionConfig,
        secret: String?,
    ): ConnectionCheck {
        val effectiveSecret = secret ?: store.secret(config.id)
        val source =
            try {
                factory.create(config, effectiveSecret).source
            } catch (e: IllegalArgumentException) {
                return ConnectionCheck.Invalid(e.message)
            }
        val check = check(source)
        if (check == ConnectionCheck.Ok) store.put(config, secret)
        return check
    }

    override suspend fun setEnabled(
        id: String,
        enabled: Boolean,
    ) = store.update(id) { it.copy(enabled = enabled) }

    override suspend fun remove(id: String) = store.remove(id)

    private suspend fun build(config: ConnectionConfig): ConnectionPlugins? =
        runCatching { factory.create(config, store.secret(config.id)) }.getOrNull()

    /** Пробный поиск: сервер ответил и пустил — подключение рабочее, даже если ничего не нашлось. */
    private suspend fun check(source: MusicSource): ConnectionCheck {
        val result =
            withTimeoutOrNull(CHECK_TIMEOUT_MS) { source.search(PROBE_QUERY, 1) }
                ?: return ConnectionCheck.Unreachable
        return when (result) {
            is PluginResult.Ok -> {
                ConnectionCheck.Ok
            }

            is PluginResult.Failure -> {
                when (result.kind) {
                    PluginResult.Kind.UNAUTHORIZED -> ConnectionCheck.Unauthorized
                    PluginResult.Kind.NETWORK, PluginResult.Kind.TIMEOUT -> ConnectionCheck.Unreachable
                    PluginResult.Kind.RATE_LIMITED -> ConnectionCheck.Ok
                    else -> ConnectionCheck.Invalid(result.message)
                }
            }
        }
    }

    private companion object {
        const val PROBE_QUERY = "a"
        const val CHECK_TIMEOUT_MS = 15_000L
    }
}
