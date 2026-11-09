package dev.fuzz.app.di

import dev.fuzz.connect.manifest.ManifestConnection
import dev.fuzz.connect.manifest.ManifestParser
import dev.fuzz.connect.script.ScriptConnection
import dev.fuzz.connect.script.ScriptHeaderParser
import dev.fuzz.connect.templates.ampache.AmpacheCredentials
import dev.fuzz.connect.templates.ampache.AmpacheSource
import dev.fuzz.connect.templates.jellyfin.JellyfinSource
import dev.fuzz.connect.templates.webdav.WebDavSource
import dev.fuzz.core.domain.connection.ConnectionConfig
import dev.fuzz.core.domain.connection.ConnectionKind
import dev.fuzz.core.domain.connection.ConnectionPlugins
import dev.fuzz.core.domain.connection.MusicSourceFactory
import dev.fuzz.core.domain.connection.RequestLine
import dev.fuzz.source.subsonic.SubsonicSource
import io.ktor.client.HttpClient
import java.net.URI
import javax.inject.Inject

/** Собирает источник по настройкам из «Подключений». Только app знает все плагины. */
internal class PluginSourceFactory
    @Inject
    constructor(
        private val client: HttpClient,
    ) : MusicSourceFactory {
        override fun create(
            config: ConnectionConfig,
            secret: String?,
            onSecretRotated: (String) -> Unit,
        ): ConnectionPlugins {
            val id = "connection:${config.id}"
            val password = secret.orEmpty()
            val source =
                when (config.kind) {
                    ConnectionKind.Subsonic -> {
                        SubsonicSource(client, url(config), config.username, password, id, config.name)
                    }

                    ConnectionKind.Jellyfin -> {
                        JellyfinSource(
                            client,
                            url(config),
                            config.username,
                            password,
                            deviceId = config.id,
                            id,
                            config.name,
                        )
                    }

                    ConnectionKind.Ampache -> {
                        val credentials =
                            if (config.username.isBlank()) {
                                AmpacheCredentials.ApiKey(password)
                            } else {
                                AmpacheCredentials.Password(config.username, password)
                            }
                        AmpacheSource(client, url(config), credentials, id, config.name)
                    }

                    ConnectionKind.WebDav -> {
                        WebDavSource(
                            client,
                            url(config),
                            config.username.ifBlank { null },
                            secret?.ifBlank { null },
                            id,
                            config.name,
                        )
                    }

                    ConnectionKind.Manifest -> {
                        return manifest(config, secret, onSecretRotated)
                    }

                    ConnectionKind.Script -> {
                        return script(config)
                    }
                }
            return ConnectionPlugins(source)
        }

        private fun manifest(
            config: ConnectionConfig,
            secret: String?,
            onSecretRotated: (String) -> Unit,
        ): ConnectionPlugins =
            when (val parsed = ManifestParser.parse(config.manifest)) {
                is ManifestParser.Result.Valid -> {
                    val connection =
                        ManifestConnection(
                            client,
                            parsed.manifest,
                            secret?.ifBlank { null },
                            onRefreshTokenRotated = onSecretRotated,
                        )
                    ConnectionPlugins(
                        source = connection.source,
                        lyrics = connection.lyrics,
                        metadata = connection.metadata,
                        journal = {
                            connection.journal().map {
                                RequestLine(it.atMs, it.path, it.status, it.durationMs, it.error)
                            }
                        },
                    )
                }

                is ManifestParser.Result.Invalid -> {
                    throw IllegalArgumentException(parsed.errors.joinToString("\n"))
                }
            }

        /** JS-плагин: описание проверяет [ScriptHeaderParser], код работает в песочнице QuickJS. */
        private fun script(config: ConnectionConfig): ConnectionPlugins =
            when (val parsed = ScriptHeaderParser.parse(config.manifest, config.script)) {
                is ScriptHeaderParser.Result.Valid -> {
                    val connection = ScriptConnection(client, parsed.header, config.script)
                    val source = connection.source
                    if (source == null) {
                        connection.close()
                        throw IllegalArgumentException(NO_SOURCE)
                    }
                    ConnectionPlugins(
                        source = source,
                        lyrics = connection.lyrics,
                        close = connection::close,
                    )
                }

                is ScriptHeaderParser.Result.Invalid -> {
                    throw IllegalArgumentException(parsed.errors.joinToString("\n"))
                }
            }

        /** Только http(s) с хостом: иначе источник ушёл бы непонятно куда. */
        private fun url(config: ConnectionConfig): String {
            val raw = config.baseUrl.trim().trimEnd('/')
            val uri = runCatching { URI(raw) }.getOrNull()
            require(uri != null && uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) {
                "Адрес должен начинаться с http:// или https://"
            }
            return raw
        }

        private companion object {
            const val NO_SOURCE = "Плагин без возможности source: Fuzz пока подключает только плагины-источники"
        }
    }
