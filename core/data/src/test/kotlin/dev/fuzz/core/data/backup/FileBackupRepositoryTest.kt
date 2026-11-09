package dev.fuzz.core.data.backup

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.protobuf.ByteString
import dev.fuzz.core.data.sync.Clock
import dev.fuzz.core.database.FuzzDatabase
import dev.fuzz.core.database.entity.ListenEntity
import dev.fuzz.core.database.entity.PlaylistEntity
import dev.fuzz.core.database.entity.PlaylistTrackEntity
import dev.fuzz.core.database.entity.ReactionEntity
import dev.fuzz.core.database.entity.SavedRefEntity
import dev.fuzz.core.database.entity.TrackEntity
import dev.fuzz.core.datastore.ConnectionProto
import dev.fuzz.core.datastore.UserPreferences
import dev.fuzz.core.datastore.UserPreferencesSerializer
import dev.fuzz.core.domain.backup.BackupFormatException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class FileBackupRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val clock =
        object : Clock() {
            override fun nowMs() = 1_000L
        }
    private val oldPhone =
        Room.inMemoryDatabaseBuilder(
            context,
            FuzzDatabase::class.java,
        ).allowMainThreadQueries().build()
    private val newPhone =
        Room.inMemoryDatabaseBuilder(
            context,
            FuzzDatabase::class.java,
        ).allowMainThreadQueries().build()

    @After
    fun tearDown() {
        oldPhone.close()
        newPhone.close()
    }

    private fun prefs(
        name: String,
        scope: CoroutineScope,
    ): DataStore<UserPreferences> =
        DataStoreFactory.create(UserPreferencesSerializer(), scope = scope) {
            folder.newFile("$name.pb").also { it.delete() }
        }

    private fun repo(
        db: FuzzDatabase,
        store: DataStore<UserPreferences>,
    ) = FileBackupRepository(context, db, db.backupDao(), store, clock)

    private fun track(
        id: String,
        artist: String,
        title: String,
        uri: String,
        source: String = "local",
    ) = TrackEntity(
        id = id,
        title = title,
        artistName = artist,
        albumTitle = null,
        durationMs = 1,
        playbackUri = uri,
        artworkUri = null,
        trackNumber = null,
        year = null,
        format = null,
        addedAt = 0,
        sourceId = source,
        updatedAt = 0,
    )

    @Test
    fun `moves likes, playlists, history and settings to a new phone`() =
        runTest {
            oldPhone.trackDao().upsert(
                listOf(
                    track("old-local", "Кино", "Кукушка", "content://media/1"),
                    track("net", "Arijit Singh", "Tum Hi Ho", "fuzz://saved/net", source = "online"),
                ),
            )
            oldPhone.savedRefDao().upsert(SavedRefEntity("net", "js-saavn", "OpenCatalog", "saavn\tabc"))
            oldPhone.reactionDao().upsert(ReactionEntity("old-local", 1, 5))
            oldPhone.reactionDao().upsert(ReactionEntity("net", 1, 5))
            oldPhone.playlistDao().insert(PlaylistEntity("p1", "Дорога", 1, 2))
            oldPhone.playlistDao().insertEntries(
                listOf(PlaylistTrackEntity("p1", 0, "old-local", 1), PlaylistTrackEntity("p1", 1, "net", 1)),
            )
            oldPhone.listenDao().insert(ListenEntity(trackId = "net", startedAt = 7, playedMs = 100, completed = true))
            val oldPrefs = prefs("old", backgroundScope)
            oldPrefs.updateData {
                it
                    .toBuilder()
                    .setTheme("dark")
                    .addConnections(
                        ConnectionProto
                            .newBuilder()
                            .setId("nav")
                            .setKind("subsonic")
                            .setSecret(ByteString.copyFromUtf8("x")),
                    ).build()
            }
            val file = File(folder.root, "fuzz.json")
            file.createNewFile()

            val saved = repo(oldPhone, oldPrefs).export(Uri.fromFile(file).toString())

            assertEquals(2, saved.likes)
            assertTrue(file.readText().contains("Кукушка"))
            assertTrue("пароль не должен попасть в копию", !file.readText().contains("\"x\""))

            // На новом телефоне тот же файл получил другой id.
            newPhone.trackDao().upsert(listOf(track("new-local", "КИНО", "Кукушка", "content://media/9")))
            val newPrefs = prefs("new", backgroundScope)

            val restored = repo(newPhone, newPrefs).restore(Uri.fromFile(file).toString())

            assertEquals(2, restored.likes)
            assertEquals(1, restored.playlists)
            assertEquals(1, restored.listens)
            assertEquals(0, restored.missingFiles)
            assertEquals(1, restored.connectionsNeedingPassword)
            assertEquals(listOf("new-local", "net"), newPhone.playlistDao().entries("p1").map { it.trackId })
            assertEquals("js-saavn", newPhone.savedRefDao().get("net")?.sourceKey)
            val settings = newPrefs.data.first()
            assertEquals("dark", settings.theme)
            assertEquals(listOf("nav"), settings.connectionsList.map { it.id })
            assertTrue(settings.connectionsList.single().secret.isEmpty)
        }

    @Test(expected = BackupFormatException::class)
    fun `foreign file is refused`() =
        runTest {
            val file = File(folder.root, "other.json").apply { writeText("""{"format":"other","createdAt":1}""") }
            repo(newPhone, prefs("x", backgroundScope)).restore(Uri.fromFile(file).toString())
        }
}
