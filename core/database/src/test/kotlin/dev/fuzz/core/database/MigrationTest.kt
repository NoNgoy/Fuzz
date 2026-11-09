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

    @Test
    fun `2 to 3 adds listening history`() {
        helper.createDatabase(DB, 2).close()

        helper.runMigrationsAndValidate(DB, 3, true).use { db ->
            db.execSQL("INSERT INTO listen_events (track_id, started_at, played_ms, completed) VALUES ('t1', 5, 7, 1)")
            db.query("SELECT track_id, played_ms, completed FROM listen_events").use { cursor ->
                cursor.moveToFirst()
                assertEquals("t1", cursor.getString(0))
                assertEquals(7, cursor.getLong(1))
                assertEquals(1, cursor.getInt(2))
            }
        }
    }

    @Test
    fun `3 to 4 adds similarity cache`() {
        helper.createDatabase(DB, 3).close()

        helper.runMigrationsAndValidate(DB, 4, true).use { db ->
            db.execSQL(
                "INSERT INTO similarity_cache (kind, `key`, payload, stored_at) VALUES ('artist', 'soad', 'x', 9)",
            )
            db.query("SELECT payload, stored_at FROM similarity_cache").use { cursor ->
                cursor.moveToFirst()
                assertEquals("x", cursor.getString(0))
                assertEquals(9, cursor.getLong(1))
            }
        }
    }

    @Test
    fun `4 to 5 keeps tracks visible`() {
        helper.createDatabase(DB, 4).use { db ->
            db.execSQL(
                """
                INSERT INTO tracks (id, title, artist_name, album_title, duration_ms, playback_uri, artwork_uri,
                    track_number, year, format, added_at, source_id, updated_at, deleted)
                VALUES ('t1', 'Кукушка', 'Кино', NULL, 1, 'content://1', NULL, NULL, NULL, 'FLAC', 0, 'local', 0, 0)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(DB, 5, true).use { db ->
            db.query("SELECT hidden FROM tracks WHERE id = 't1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
