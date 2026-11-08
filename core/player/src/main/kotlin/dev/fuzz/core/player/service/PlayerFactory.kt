package dev.fuzz.core.player.service

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dev.fuzz.core.domain.player.PlaybackHeaders
import dev.fuzz.core.player.FuzzPlayer

internal object PlayerFactory {
    @OptIn(UnstableApi::class)
    fun create(
        context: Context,
        headers: PlaybackHeaders,
    ): FuzzPlayer {
        val attributes =
            AudioAttributes
                .Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
        val exo =
            ExoPlayer
                .Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSources(context, headers)))
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

    /** Локальные файлы и http; к сетевым ссылкам добавляются заголовки авторизации источника. */
    @OptIn(UnstableApi::class)
    private fun dataSources(
        context: Context,
        headers: PlaybackHeaders,
    ): DataSource.Factory =
        ResolvingDataSource.Factory(DefaultDataSource.Factory(context)) { spec ->
            val extra = if (spec.uri.scheme in HTTP_SCHEMES) headers.forUri(spec.uri.toString()) else emptyMap()
            if (extra.isEmpty()) spec else spec.withAdditionalHeaders(extra)
        }

    private val HTTP_SCHEMES = setOf("http", "https")
    private const val SEEK_INCREMENT_MS = 10_000L
}
