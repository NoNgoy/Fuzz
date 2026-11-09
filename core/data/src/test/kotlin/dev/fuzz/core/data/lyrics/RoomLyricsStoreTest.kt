package dev.fuzz.core.data.lyrics

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.fuzz.core.data.sync.Clock
import dev.fuzz.core.database.FuzzDatabase
import dev.fuzz.core.domain.lyrics.LyricsResult
import dev.fuzz.core.domain.plugin.Lyrics
import dev.fuzz.core.domain.plugin.LyricsLine
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomLyricsStoreTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FuzzDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val store =
        RoomLyricsStore(
            db.lyricsDao(),
            object : Clock() {
                override fun nowMs() = 1L
            },
        )

    @After
    fun tearDown() = db.close()

    @Test
    fun `synced and plain lines survive a round trip`() =
        runTest {
            val synced = Lyrics(listOf(LyricsLine(0, "Утекай"), LyricsLine(12_500, "В подворотне\tнас ждёт")), true)
            val plain = Lyrics(listOf(LyricsLine(null, "Кукушка"), LyricsLine(null, "")), false)

            store.put("a", LyricsResult.Found(synced, "LRCLIB"))
            store.put("b", LyricsResult.Found(plain, "Мой API"))

            assertEquals(
                LyricsResult.Found(
                    Lyrics(listOf(LyricsLine(0, "Утекай"), LyricsLine(12_500, "В подворотне нас ждёт")), true),
                    "LRCLIB",
                ),
                store.get("a"),
            )
            assertEquals(LyricsResult.Found(plain, "Мой API"), store.get("b"))
            assertNull(store.get("c"))
        }
}
