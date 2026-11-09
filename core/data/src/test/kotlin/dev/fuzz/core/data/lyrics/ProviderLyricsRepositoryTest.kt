package dev.fuzz.core.data.lyrics

import dev.fuzz.core.domain.connection.ConnectedSources
import dev.fuzz.core.domain.lyrics.LyricsResult
import dev.fuzz.core.domain.plugin.Lyrics
import dev.fuzz.core.domain.plugin.LyricsLine
import dev.fuzz.core.domain.plugin.LyricsProvider
import dev.fuzz.core.domain.plugin.MetadataProvider
import dev.fuzz.core.domain.plugin.MusicSource
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.TrackRef
import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderLyricsRepositoryTest {
    private val track = Track(TrackId("1"), "Утекай", "Мумий Тролль", null, 236_000, "content://1")
    private val plain = Lyrics(listOf(LyricsLine(null, "Утекай")), synced = false)
    private val synced = Lyrics(listOf(LyricsLine(1_000, "Утекай")), synced = true)

    @Test
    fun `синхронный текст важнее обычного, свой API спрашивается первым`() =
        runTest {
            val own = FakeProvider("Мой API", PluginResult.Ok(plain))
            val lrclib = FakeProvider("LRCLIB", PluginResult.Ok(synced))
            val repository = ProviderLyricsRepository(listOf(lrclib), Connected(listOf(own)), MemoryStore())
            assertEquals(LyricsResult.Found(synced, "LRCLIB"), repository.lyrics(track))
            assertEquals(1, own.calls)

            repository.lyrics(track)
            assertEquals("второй раз из памяти", 1, lrclib.calls)
        }

    @Test
    fun `без сети не запоминается, нет текста запоминается`() =
        runTest {
            val offline = FakeProvider("LRCLIB", PluginResult.Failure(PluginResult.Kind.NETWORK))
            val repository = ProviderLyricsRepository(listOf(offline), Connected(emptyList()), MemoryStore())
            assertEquals(LyricsResult.Unavailable, repository.lyrics(track))
            repository.lyrics(track)
            assertEquals(2, offline.calls)

            offline.answer = PluginResult.Failure(PluginResult.Kind.NOT_FOUND)
            assertEquals(LyricsResult.NotFound, repository.lyrics(track))
            repository.lyrics(track)
            assertEquals(3, offline.calls)
        }

    @Test
    fun `найденный текст открывается без сети`() =
        runTest {
            val store = MemoryStore()
            val lrclib = FakeProvider("LRCLIB", PluginResult.Ok(synced))
            ProviderLyricsRepository(listOf(lrclib), Connected(emptyList()), store).lyrics(track)

            // Новый запуск приложения: память пуста, сети нет.
            lrclib.answer = PluginResult.Failure(PluginResult.Kind.NETWORK)
            val restarted = ProviderLyricsRepository(listOf(lrclib), Connected(emptyList()), store)

            assertEquals(LyricsResult.Found(synced, "LRCLIB"), restarted.lyrics(track))
            assertEquals(1, lrclib.calls)
        }

    private class MemoryStore : LyricsStore {
        private val map = mutableMapOf<String, LyricsResult.Found>()

        override suspend fun get(key: String) = map[key]

        override suspend fun put(
            key: String,
            found: LyricsResult.Found,
        ) {
            map[key] = found
        }
    }

    private class FakeProvider(
        override val displayName: String,
        var answer: PluginResult<Lyrics>,
    ) : LyricsProvider {
        override val id = displayName
        var calls = 0

        override suspend fun lyrics(track: TrackRef): PluginResult<Lyrics> {
            calls++
            return answer
        }
    }

    private class Connected(
        lyrics: List<LyricsProvider>,
    ) : ConnectedSources {
        override val sources = MutableStateFlow(emptyList<MusicSource>())
        override val lyrics = MutableStateFlow(lyrics)
        override val metadata = MutableStateFlow(emptyList<MetadataProvider>())
    }
}
