package dev.fuzz.source.local

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import dev.fuzz.core.domain.library.ScanResult
import dev.fuzz.core.domain.onboarding.MusicFolder
import dev.fuzz.core.domain.settings.ScanRules
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaStoreScannerTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val provider = FakeMediaProvider()
    private val music = listOf(MusicFolder("Music/", "Музыка"))
    private val noExclusions = ScanRules(skipVoiceNotes = false, minDurationSec = 0)

    init {
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, provider)
    }

    @Test
    fun `without permission nothing is read`() =
        runTest {
            assertEquals(ScanResult.NoPermission, MediaStoreScanner(app).scan(music))
            assertEquals(0, provider.queries)
        }

    @Test
    fun `rows become tracks with tags, format and artwork`() =
        runTest {
            shadowOf(app).grantPermissions(android.Manifest.permission.READ_MEDIA_AUDIO)
            provider.rows +=
                arrayOf(
                    42L,
                    "Кукушка",
                    "kukushka.flac",
                    "Кино",
                    "Чёрный альбом",
                    397_000L,
                    1002,
                    1990,
                    "audio/flac",
                    1_700_000_000L,
                    "external_primary",
                )
            provider.rows +=
                arrayOf(
                    43L,
                    "<unknown>",
                    "demo take 3.mp3",
                    "<unknown>",
                    "<unknown>",
                    60_000L,
                    0,
                    0,
                    "audio/mpeg",
                    1L,
                    "external_primary",
                )

            val tracks = (MediaStoreScanner(app).scan(music, noExclusions) as ScanResult.Success).tracks

            val kukushka = tracks[0]
            assertEquals("local:external_primary:42", kukushka.id)
            assertEquals("Кукушка", kukushka.title)
            assertEquals("Кино", kukushka.artistName)
            assertEquals(2, kukushka.trackNumber)
            assertEquals("FLAC", kukushka.format)
            assertEquals(1_700_000_000_000L, kukushka.addedAtMs)
            assertEquals("content://media/external_primary/audio/media/42", kukushka.playbackUri)
            assertEquals("content://media/external_primary/audio/media/42/albumart", kukushka.artworkUri)

            val untagged = tracks[1]
            assertEquals("demo take 3", untagged.title)
            assertEquals("Неизвестный исполнитель", untagged.artistName)
            assertEquals(null, untagged.albumTitle)
            assertEquals(null, untagged.year)
            assertEquals(listOf("Music/%"), provider.lastArgs)
        }

    @Test
    fun `short files and voice notes are excluded in the query`() =
        runTest {
            shadowOf(app).grantPermissions(android.Manifest.permission.READ_MEDIA_AUDIO)

            MediaStoreScanner(app).scan(music, ScanRules(skipVoiceNotes = true, minDurationSec = 30))

            assertEquals("Music/%", provider.lastArgs[0])
            assertEquals("30000", provider.lastArgs[1])
            assertTrue("%MIUI/sound_recorder/%" in provider.lastArgs)
            assertTrue(provider.lastSelection.orEmpty().contains("is_recording = 0"))
        }

    private class FakeMediaProvider : ContentProvider() {
        val rows = mutableListOf<Array<Any?>>()
        var queries = 0
        var lastArgs: List<String> = emptyList()
        var lastSelection: String? = null

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            queries++
            lastArgs = selectionArgs.orEmpty().toList()
            lastSelection = selection
            return MatrixCursor(projection).apply { rows.forEach { addRow(it) } }
        }

        override fun onCreate() = true

        override fun getType(uri: Uri): String? = null

        override fun insert(
            uri: Uri,
            values: ContentValues?,
        ): Uri? = null

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ) = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ) = 0
    }
}
