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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dev.fuzz.core.domain.player.PlaybackHeaders
import dev.fuzz.core.domain.player.SavedStreams
import dev.fuzz.core.player.FuzzPlayer
import kotlinx.coroutines.runBlocking
import java.io.IOException

internal object PlayerFactory {
    @OptIn(UnstableApi::class)
    fun create(
        context: Context,
        headers: PlaybackHeaders,
        saved: SavedStreams,
    ): FuzzPlayer {
        val attributes = musicAttributes()
        val exo =
            ExoPlayer
                .Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSources(context, headers, saved)))
                // Аудиофокус: пауза при звонке, приглушение при навигаторе.
                .setAudioAttributes(attributes, true)
                // Пауза при отключении наушников (настраиваемое поведение появится в настройках).
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
    ): ExoPlayer =
        ExoPlayer
            .Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSources(context, headers, saved)))
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
     */
    @OptIn(UnstableApi::class)
    private fun dataSources(
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
