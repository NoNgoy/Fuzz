package dev.fuzz.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.fuzz.app.BuildConfig
import dev.fuzz.core.data.wave.ConnectedCatalog
import dev.fuzz.core.data.wave.ExternalTrackRegistry
import dev.fuzz.core.data.wave.ProviderSimilarityGraph
import dev.fuzz.core.data.wave.SourceExternalCatalog
import dev.fuzz.core.domain.connection.ConnectedSources
import dev.fuzz.core.domain.plugin.MetadataProvider
import dev.fuzz.core.domain.plugin.Providers
import dev.fuzz.core.domain.search.CatalogSource
import dev.fuzz.core.domain.search.OnlineOrigin
import dev.fuzz.engine.recommend.model.TrackOrigin
import dev.fuzz.engine.recommend.port.ExternalCatalog
import dev.fuzz.engine.recommend.port.SimilarityGraph
import dev.fuzz.meta.lastfm.LastFmProvider
import dev.fuzz.meta.listenbrainz.ListenBrainzProvider
import dev.fuzz.source.deezerpreview.DeezerPreviewSource
import dev.fuzz.source.jamendo.JamendoSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

/**
 * Подключения, которые питают волну. Ключи Last.fm и Jamendo в репозиторий не попадают: они приходят
 * из gradle-свойств `fuzz.lastfm.apiKey` и `fuzz.jamendo.clientId` (или переменных окружения) в BuildConfig.
 * Без ключа плагин просто не подключается, волна работает на остальных.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object PluginsModule {
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient =
        HttpClient(OkHttp) {
            expectSuccess = false
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout) {
                connectTimeoutMillis = TIMEOUT_MS
                requestTimeoutMillis = TIMEOUT_MS
            }
            defaultRequest { headers.append(HttpHeaders.UserAgent, USER_AGENT) }
        }

    @Provides
    @Singleton
    fun provideSimilarityGraph(client: HttpClient): SimilarityGraph {
        val providers =
            buildList<Pair<MetadataProvider, Float>> {
                add(ListenBrainzProvider(client) to LISTENBRAINZ_WEIGHT)
                if (BuildConfig.LASTFM_API_KEY.isNotBlank()) {
                    add(LastFmProvider(client, BuildConfig.LASTFM_API_KEY) to LASTFM_WEIGHT)
                }
            }
        return ProviderSimilarityGraph(providers)
    }

    @Provides
    @Singleton
    fun provideCatalogs(
        client: HttpClient,
        registry: ExternalTrackRegistry,
        connected: ConnectedSources,
    ): List<@JvmSuppressWildcards ExternalCatalog> =
        buildList {
            // Свои серверы из «Подключений»: набор читается на каждый запрос, включение работает сразу.
            add(ConnectedCatalog(connected, registry))
            add(SourceExternalCatalog(DeezerPreviewSource(client), TrackOrigin.Preview, Providers.DEEZER, registry))
            if (BuildConfig.JAMENDO_CLIENT_ID.isNotBlank()) {
                val jamendo = JamendoSource(client, BuildConfig.JAMENDO_CLIENT_ID)
                add(SourceExternalCatalog(jamendo, TrackOrigin.OpenCatalog, Providers.JAMENDO, registry))
            }
        }

    /** Встроенные каталоги для экрана поиска; Jamendo только с ключом. */
    @Provides
    @Singleton
    fun provideSearchCatalogs(client: HttpClient): List<@JvmSuppressWildcards CatalogSource> =
        buildList {
            add(CatalogSource(DeezerPreviewSource(client), OnlineOrigin.Preview, Providers.DEEZER))
            if (BuildConfig.JAMENDO_CLIENT_ID.isNotBlank()) {
                add(
                    CatalogSource(
                        JamendoSource(client, BuildConfig.JAMENDO_CLIENT_ID),
                        OnlineOrigin.OpenCatalog,
                        Providers.JAMENDO,
                    ),
                )
            }
        }

    private const val TIMEOUT_MS = 10_000L
    private const val USER_AGENT = "Fuzz/0.1 (+https://github.com/NoNgoy/Fuzz)"
    private const val LASTFM_WEIGHT = 1f
    private const val LISTENBRAINZ_WEIGHT = 0.8f
}
