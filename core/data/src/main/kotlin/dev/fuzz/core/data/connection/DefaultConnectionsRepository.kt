package dev.fuzz.core.data.connection

import dev.fuzz.core.domain.connection.ConnectedSources
import dev.fuzz.core.domain.connection.ConnectionCheck
import dev.fuzz.core.domain.connection.ConnectionConfig
import dev.fuzz.core.domain.connection.ConnectionPlugins
import dev.fuzz.core.domain.connection.ConnectionsRepository
import dev.fuzz.core.domain.connection.MusicSourceFactory
import dev.fuzz.core.domain.connection.RequestLine
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    private val scope: CoroutineScope,
) : ConnectionsRepository,
    ConnectedSources {
    @Inject
    constructor(store: ConnectionsStore, factory: MusicSourceFactory) :
        this(store, factory, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    override val connections: Flow<List<ConnectionConfig>> = store.connections

    /**
     * Плагины включённых подключений по id. При любой правке набор собирается заново, а старые
     * плагины закрываются: у JS-плагина это песочница QuickJS, её нельзя бросать открытой.
     */
    private val plugins: StateFlow<Map<String, ConnectionPlugins>> =
        store.connections
            .map { list -> list.filter { it.enabled } }
            .distinctUntilChanged()
            .runningFold(emptyMap<String, ConnectionPlugins>()) { old, list ->
                old.values.forEach { it.close() }
                list.mapNotNull { config -> build(config)?.let { config.id to it } }.toMap()
            }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    override val sources: StateFlow<List<MusicSource>> =
        plugins.map { map -> map.values.map { it.source } }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val lyrics: StateFlow<List<LyricsProvider>> =
        plugins.map { map -> map.values.mapNotNull { it.lyrics } }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val metadata: StateFlow<List<MetadataProvider>> =
        plugins
            .map { map -> map.values.mapNotNull { it.metadata } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun save(
        config: ConnectionConfig,
        secret: String?,
    ): ConnectionCheck {
        val effectiveSecret = secret ?: store.secret(config.id)
        var rotated: String? = null
        val check = probe(config, effectiveSecret) { rotated = it }
        if (check == ConnectionCheck.Ok) store.put(config, rotated ?: secret)
        return check
    }

    override suspend fun setEnabled(
        id: String,
        enabled: Boolean,
    ) = store.update(id) { it.copy(enabled = enabled) }

    override suspend fun remove(id: String) = store.remove(id)

    override suspend fun move(
        id: String,
        offset: Int,
    ) = store.move(id, offset)

    override suspend fun check(id: String): ConnectionCheck {
        plugins.value[id]?.let { return check(it.source) }
        // Выключенное подключение не собрано: проверяем временной копией.
        val config = store.connections.first().firstOrNull { it.id == id } ?: return ConnectionCheck.Invalid(null)
        return probe(config, store.secret(id)) { scope.launch { store.rotateSecret(id, it) } }
    }

    /** Временные плагины только для проверки: после неё сразу закрываются. */
    private suspend fun probe(
        config: ConnectionConfig,
        secret: String?,
        onSecretRotated: (String) -> Unit,
    ): ConnectionCheck {
        val temporary =
            try {
                factory.create(config, secret, onSecretRotated)
            } catch (e: IllegalArgumentException) {
                return ConnectionCheck.Invalid(e.message)
            }
        return try {
            check(temporary.source)
        } finally {
            temporary.close()
        }
    }

    override fun journal(id: String): List<RequestLine> = plugins.value[id]?.journal?.invoke().orEmpty()

    private suspend fun build(config: ConnectionConfig): ConnectionPlugins? =
        runCatching {
            factory.create(config, store.secret(config.id)) { scope.launch { store.rotateSecret(config.id, it) } }
        }.getOrNull()

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
