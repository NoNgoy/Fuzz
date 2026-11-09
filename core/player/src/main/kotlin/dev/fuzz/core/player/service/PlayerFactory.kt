package dev.fuzz.core.player.service

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dev.fuzz.core.domain.player.PlaybackHeaders
import dev.fuzz.core.domain.player.SavedStreams
import dev.fuzz.core.player.FuzzPlayer
import dev.fuzz.core.player.cache.RoutingDataSource
import dev.fuzz.core.player.cache.StreamCache
import kotlinx.coroutines.runBlocking
import java.io.IOException

internal object PlayerFactory {
    @OptIn(UnstableApi::class)
    fun create(
        context: Context,
        headers: PlaybackHeaders,
        saved: SavedStreams,
        cache: StreamCache,
    ): FuzzPlayer {
        val attributes = musicAttributes()
        val exo =
            ExoPlayer
                .Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSources(context, headers, saved, cache)))
                // Аудиофокус: пауза при звонке, приглушение при навигаторе.
                .setAudioAttributes(attributes, true)
                // Пауза при отключении наушников; в настройках можно выключить (см. FuzzPlaybackService).
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
                .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
                .build()
        return FuzzPlayer(exo)
    }

    /**
     * Скрытый плеер для хвоста трека при кроссфейде: те же источники, но без аудиофокуса и реакции
     * на наушники — этим управляет основной плеер, а хвост живёт считанные секунды.
     */
    @OptIn(UnstableApi::class)
    fun createTail(
        context: Context,
        headers: PlaybackHeaders,
        saved: SavedStreams,
        cache: StreamCache,
    ): ExoPlayer =
        ExoPlayer
            .Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSources(context, headers, saved, cache)))
            .setAudioAttributes(musicAttributes(), false)
            .build()

    private fun musicAttributes() =
        AudioAttributes
            .Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

    /**
     * Локальные файлы и http; к сетевым ссылкам добавляются заголовки авторизации источника.
     * Сохранённый трек из сети (`fuzz://saved/<id>`) получает свежую ссылку у источника на потоке загрузки.
     * Потоки из сети идут через [StreamCache], если кэш включён; ключ — исходная ссылка, поэтому
     * сохранённый трек находится в кэше, даже когда источник выдал новую ссылку с другим токеном.
     */
    @OptIn(UnstableApi::class)
    private fun dataSources(
        context: Context,
        headers: PlaybackHeaders,
        saved: SavedStreams,
        cache: StreamCache,
    ): DataSource.Factory {
        val resolving = resolving(context, headers, saved)
        val cached by lazy {
            CacheDataSource
                .Factory()
                .setCache(cache.cache)
                .setUpstreamDataSourceFactory(resolving)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }
        return RoutingDataSource.Factory(resolving) { if (cache.enabled) cached else null }
    }

    @OptIn(UnstableApi::class)
    private fun resolving(
        context: Context,
        headers: PlaybackHeaders,
        saved: SavedStreams,
    ): DataSource.Factory =
        ResolvingDataSource.Factory(DefaultDataSource.Factory(context)) { original ->
            val spec =
                if (original.uri.scheme == SavedStreams.SCHEME) {
                    val id = original.uri.lastPathSegment.orEmpty()
                    val real =
                        runBlocking { saved.resolve(id) }
                            ?: throw IOException("Источник трека недоступен: проверьте подключение и сеть")
                    original.withUri(real.toUri())
                } else {
                    original
                }
            val extra = if (spec.uri.scheme in HTTP_SCHEMES) headers.forUri(spec.uri.toString()) else emptyMap()
            if (extra.isEmpty()) spec else spec.withAdditionalHeaders(extra)
        }

    private val HTTP_SCHEMES = setOf("http", "https")
    private const val SEEK_INCREMENT_MS = 10_000L
}
