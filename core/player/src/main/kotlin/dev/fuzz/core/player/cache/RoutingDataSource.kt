package dev.fuzz.core.player.cache

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Файлы с телефона читаются напрямую, а потоки из сети — через [remote] (кэш), когда он включён:
 * кэшировать локальные файлы незачем, они и так на диске.
 */
@OptIn(UnstableApi::class)
internal class RoutingDataSource(
    private val local: DataSource,
    private val remote: () -> DataSource?,
) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        local.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val source =
            if (dataSpec.uri.scheme in LOCAL_SCHEMES) {
                local
            } else {
                remote()?.also { r -> listeners.forEach(r::addTransferListener) } ?: local
            }
        active = source
        return source.open(dataSpec)
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = checkNotNull(active).read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders.orEmpty()

    override fun close() {
        try {
            active?.close()
        } finally {
            active = null
        }
    }

    class Factory(
        private val local: DataSource.Factory,
        private val remote: () -> DataSource.Factory?,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            RoutingDataSource(local.createDataSource()) { remote()?.createDataSource() }
    }

    private companion object {
        val LOCAL_SCHEMES = setOf("content", "file", "android.resource", "asset")
    }
}
