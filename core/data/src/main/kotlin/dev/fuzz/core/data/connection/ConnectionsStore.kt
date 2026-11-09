package dev.fuzz.core.data.connection

import androidx.datastore.core.DataStore
import com.google.protobuf.ByteString
import dev.fuzz.core.datastore.ConnectionProto
import dev.fuzz.core.datastore.UserPreferences
import dev.fuzz.core.domain.connection.ConnectionConfig
import dev.fuzz.core.domain.connection.ConnectionKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Подключения в DataStore; секрет лежит рядом, но только зашифрованным [SecretCipher]. */
internal class ConnectionsStore
    @Inject
    constructor(
        private val dataStore: DataStore<UserPreferences>,
        private val cipher: SecretCipher,
    ) {
        val connections: Flow<List<ConnectionConfig>> =
            dataStore.data.map { prefs -> prefs.connectionsList.mapNotNull { it.toConfig() } }

        suspend fun secret(id: String): String? =
            dataStore.data
                .first()
                .connectionsList
                .firstOrNull { it.id == id }
                ?.takeIf { !it.secret.isEmpty }
                ?.let { cipher.decrypt(it.secret.toByteArray()) }

        /** [secret] = null оставляет сохранённый, пустая строка стирает. */
        suspend fun put(
            config: ConnectionConfig,
            secret: String?,
        ) {
            dataStore.updateData { prefs ->
                val old = prefs.connectionsList.firstOrNull { it.id == config.id }
                val encrypted =
                    when {
                        secret == null -> old?.secret ?: ByteString.EMPTY
                        secret.isEmpty() -> ByteString.EMPTY
                        else -> ByteString.copyFrom(cipher.encrypt(secret))
                    }
                val proto =
                    ConnectionProto
                        .newBuilder()
                        .setId(config.id)
                        .setKind(config.kind.name)
                        .setName(config.name)
                        .setBaseUrl(config.baseUrl)
                        .setUsername(config.username)
                        .setManifest(config.manifest)
                        .setScript(config.script)
                        .setEnabled(config.enabled)
                        .setSecret(encrypted)
                        .build()
                val list = prefs.connectionsList.toMutableList()
                val index = list.indexOfFirst { it.id == config.id }
                if (index >= 0) list[index] = proto else list += proto
                prefs.toBuilder().clearConnections().addAllConnections(list).build()
            }
        }

        suspend fun update(
            id: String,
            change: (ConnectionConfig) -> ConnectionConfig,
        ) {
            val current = connections.first().firstOrNull { it.id == id } ?: return
            put(change(current), secret = null)
        }

        /** Сервер выдал новый секрет (refresh token): заменить, ничего больше не трогая. */
        suspend fun rotateSecret(
            id: String,
            secret: String,
        ) {
            val current = connections.first().firstOrNull { it.id == id } ?: return
            put(current, secret)
        }

        suspend fun move(
            id: String,
            offset: Int,
        ) {
            dataStore.updateData { prefs ->
                val list = prefs.connectionsList.toMutableList()
                val from = list.indexOfFirst { it.id == id }
                if (from < 0) return@updateData prefs
                val to = (from + offset).coerceIn(0, list.lastIndex)
                if (to == from) return@updateData prefs
                list.add(to, list.removeAt(from))
                prefs.toBuilder().clearConnections().addAllConnections(list).build()
            }
        }

        suspend fun remove(id: String) {
            dataStore.updateData { prefs ->
                prefs
                    .toBuilder()
                    .clearConnections()
                    .addAllConnections(prefs.connectionsList.filterNot { it.id == id })
                    .build()
            }
        }

        private fun ConnectionProto.toConfig(): ConnectionConfig? {
            val kind = ConnectionKind.entries.firstOrNull { it.name == kind } ?: return null
            return ConnectionConfig(
                id = id,
                kind = kind,
                name = name,
                baseUrl = baseUrl,
                username = username,
                manifest = manifest,
                script = script,
                enabled = enabled,
                hasSecret = !secret.isEmpty,
            )
        }
    }
