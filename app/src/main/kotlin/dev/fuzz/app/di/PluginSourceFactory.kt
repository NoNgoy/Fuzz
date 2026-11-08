package dev.fuzz.app.di

import dev.fuzz.connect.manifest.ManifestConnection
import dev.fuzz.connect.manifest.ManifestParser
import dev.fuzz.connect.templates.ampache.AmpacheCredentials
import dev.fuzz.connect.templates.ampache.AmpacheSource
import dev.fuzz.connect.templates.jellyfin.JellyfinSource
import dev.fuzz.connect.templates.webdav.WebDavSource
import dev.fuzz.core.domain.connection.ConnectionConfig
import dev.fuzz.core.domain.connection.ConnectionKind
import dev.fuzz.core.domain.connection.ConnectionPlugins
import dev.fuzz.core.domain.connection.MusicSourceFactory
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
                        when (val parsed = ManifestParser.parse(config.manifest)) {
                            is ManifestParser.Result.Valid -> {
                                val connection = ManifestConnection(client, parsed.manifest, secret?.ifBlank { null })
                                return ConnectionPlugins(connection.source, connection.lyrics, connection.metadata)
                            }

                            is ManifestParser.Result.Invalid -> {
                                throw IllegalArgumentException(
                                    parsed.errors.joinToString("\n"),
                                )
                            }
                        }
                    }
                }
            return ConnectionPlugins(source)
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
    }
