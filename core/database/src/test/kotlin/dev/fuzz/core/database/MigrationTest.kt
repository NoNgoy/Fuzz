package dev.fuzz.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FuzzDatabase::class.java)

    @Test
    fun `1 to 2 keeps tracks and adds playlists`() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO tracks (id, title, artist_name, album_title, duration_ms, playback_uri, artwork_uri,
                    track_number, year, format, added_at, source_id, updated_at, deleted)
                VALUES ('t1', 'Кукушка', 'Кино', NULL, 1, 'content://1', NULL, NULL, NULL, 'FLAC', 0, 'local', 0, 0)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(DB, 2, true).use { db ->
            db.query("SELECT title FROM tracks").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Кукушка", cursor.getString(0))
            }
            db.query("SELECT COUNT(*) FROM playlists").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
