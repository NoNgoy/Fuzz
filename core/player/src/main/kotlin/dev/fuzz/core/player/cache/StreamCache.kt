package dev.fuzz.core.player.cache

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.TreeSet
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Кэш потоковых треков на диске: что уже звучало с сервера, второй раз играет без сети и без трафика.
 * Один на процесс (SimpleCache не открывается дважды на одной папке). Предел меняется в настройках
 * без перезапуска: лишнее удаляется сразу, начиная с давно не звучавшего.
 */
@OptIn(UnstableApi::class)
@Singleton
class StreamCache
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val evictor = AdjustableLruEvictor()
        val cache: Cache by lazy {
            SimpleCache(File(context.cacheDir, FOLDER), evictor, StandaloneDatabaseProvider(context))
        }

        @Volatile
        var enabled: Boolean = false
            private set

        fun setLimit(megabytes: Int) {
            enabled = megabytes > 0
            evictor.maxBytes = megabytes.toLong() * BYTES_PER_MB
            evictor.trim(cache)
        }

        private companion object {
            const val FOLDER = "stream"
            const val BYTES_PER_MB = 1024L * 1024
        }
    }

/** LRU как в Media3, только предел можно менять на ходу. */
@OptIn(UnstableApi::class)
internal class AdjustableLruEvictor : CacheEvictor {
    @Volatile
    var maxBytes: Long = 0
    private val spans =
        TreeSet<CacheSpan>(compareBy<CacheSpan> { it.lastTouchTimestamp }.thenBy { it.key }.thenBy { it.position })
    private var size = 0L

    override fun requiresCacheSpanTouches() = true

    override fun onCacheInitialized() = Unit

    override fun onStartFile(
        cache: Cache,
        key: String,
        position: Long,
        length: Long,
    ) {
        if (length != androidx.media3.common.C.LENGTH_UNSET.toLong()) evict(cache, length)
    }

    override fun onSpanAdded(
        cache: Cache,
        span: CacheSpan,
    ) {
        synchronized(spans) {
            spans.add(span)
            size += span.length
        }
        evict(cache, 0)
    }

    override fun onSpanRemoved(
        cache: Cache,
        span: CacheSpan,
    ) {
        synchronized(spans) {
            if (spans.remove(span)) size -= span.length
        }
    }

    override fun onSpanTouched(
        cache: Cache,
        oldSpan: CacheSpan,
        newSpan: CacheSpan,
    ) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    fun trim(cache: Cache) = evict(cache, 0)

    private fun evict(
        cache: Cache,
        requiredSpace: Long,
    ) {
        while (true) {
            val oldest =
                synchronized(spans) {
                    if (spans.isEmpty() || size + requiredSpace <= maxBytes) null else spans.first()
                } ?: return
            cache.removeSpan(oldest)
            // removeSpan сообщает onSpanRemoved; если span уже ушёл другим путём, убираем сами.
            synchronized(spans) { if (spans.remove(oldest)) size -= oldest.length }
        }
    }
}
