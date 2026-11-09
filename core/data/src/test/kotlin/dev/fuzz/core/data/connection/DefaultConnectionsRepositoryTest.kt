package dev.fuzz.core.data.connection

import androidx.datastore.core.DataStoreFactory
import dev.fuzz.core.datastore.UserPreferencesSerializer
import dev.fuzz.core.domain.connection.ConnectionCheck
import dev.fuzz.core.domain.connection.ConnectionConfig
import dev.fuzz.core.domain.connection.ConnectionKind
import dev.fuzz.core.domain.connection.ConnectionPlugins
import dev.fuzz.core.domain.connection.MusicSourceFactory
import dev.fuzz.core.domain.connection.RequestLine
import dev.fuzz.core.domain.plugin.MusicSource
import dev.fuzz.core.domain.plugin.PluginResult
import dev.fuzz.core.domain.plugin.SearchResult
import dev.fuzz.core.domain.plugin.SourceCapability
import dev.fuzz.core.domain.plugin.TrackRef
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultConnectionsRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private val config = ConnectionConfig("c1", ConnectionKind.Subsonic, "Дом", "https://nd.example.ru", "sasha")

    /** Пароль «good» пускает, остальные нет; секрет в хранилище перевёрнут, чтобы было видно шифрование. */
    private val factory =
        object : MusicSourceFactory {
            val created = mutableListOf<String?>()
            var closed = 0

            /** Пароль «rotate» сервер принимает и тут же меняет на «good», как OAuth 2 с refresh token. */
            override fun create(
                config: ConnectionConfig,
                secret: String?,
                onSecretRotated: (String) -> Unit,
            ): ConnectionPlugins {
                require(config.baseUrl.startsWith("https://")) { "плохой адрес" }
                created += secret
                if (secret == "rotate") onSecretRotated("good")
                return ConnectionPlugins(
                    source = FakeSource(config.id, secret == "good" || secret == "rotate"),
                    journal = { listOf(RequestLine(1, "/search", 200, 5)) },
                    close = { closed++ },
                )
            }
        }

    private val cipher =
        object : SecretCipher {
            override fun encrypt(plain: String) = plain.reversed().toByteArray()

            override fun decrypt(data: ByteArray) = String(data).reversed()
        }

    private fun TestScope.repository(): Pair<DefaultConnectionsRepository, ConnectionsStore> {
        val dataStore =
            DataStoreFactory.create(UserPreferencesSerializer(), scope = backgroundScope) {
                folder.newFile("prefs.pb").also { it.delete() }
            }
        val store = ConnectionsStore(dataStore, cipher)
        return DefaultConnectionsRepository(store, factory, backgroundScope) to store
    }

    @Test
    fun `рабочее подключение сохраняется с зашифрованным паролем`() =
        runTest(UnconfinedTestDispatcher()) {
            val (repository, store) = repository()
            assertEquals(ConnectionCheck.Ok, repository.save(config, "good"))

            val saved = repository.connections.first().single()
            assertTrue(saved.hasSecret)
            assertEquals("good", store.secret("c1"))
            assertEquals(listOf("c1"), repository.sources.value.map { it.id })
        }

    @Test
    fun `неверный пароль и плохой адрес не сохраняются`() =
        runTest(UnconfinedTestDispatcher()) {
            val (repository, _) = repository()
            assertEquals(ConnectionCheck.Unauthorized, repository.save(config, "bad"))
            assertEquals(
                ConnectionCheck.Invalid("плохой адрес"),
                repository.save(config.copy(baseUrl = "ftp://x"), "good"),
            )
            assertTrue(repository.connections.first().isEmpty())
        }

    @Test
    fun `правка без пароля берёт сохранённый, выключение убирает источник`() =
        runTest(UnconfinedTestDispatcher()) {
            val (repository, store) = repository()
            repository.save(config, "good")
            assertEquals(ConnectionCheck.Ok, repository.save(config.copy(name = "Дача"), secret = null))
            assertEquals("good", store.secret("c1"))
            assertEquals("Дача", repository.connections.first().single().name)

            repository.setEnabled("c1", false)
            assertFalse(repository.connections.first().single().enabled)
            assertTrue(repository.sources.value.isEmpty())

            repository.remove("c1")
            assertTrue(repository.connections.first().isEmpty())
            assertNull(store.secret("c1"))
        }

    @Test
    fun `новый секрет от сервера сохраняется`() =
        runTest(UnconfinedTestDispatcher()) {
            val (repository, store) = repository()
            assertEquals(ConnectionCheck.Ok, repository.save(config, "rotate"))
            assertEquals("good", store.secret("c1"))
        }

    @Test
    fun `проверка и журнал сохранённого подключения`() =
        runTest(UnconfinedTestDispatcher()) {
            val (repository, _) = repository()
            repository.save(config, "good")
            assertEquals(ConnectionCheck.Ok, repository.check("c1"))
            assertEquals("/search", repository.journal("c1").single().path)

            repository.setEnabled("c1", false)
            assertEquals(ConnectionCheck.Ok, repository.check("c1"))
            assertTrue(repository.journal("c1").isEmpty())
            assertEquals(ConnectionCheck.Invalid(null), repository.check("нет такого"))
        }

    @Test
    fun `пересобранные и пробные плагины закрываются`() =
        runTest(UnconfinedTestDispatcher()) {
            val (repository, _) = repository()
            repository.save(config, "good")
            // Пробный плагин закрыт сразу после проверки.
            assertEquals(1, factory.closed)
            repository.remove("c1")
            // Плагин подключения закрыт при удалении.
            assertEquals(2, factory.closed)
        }

    private class FakeSource(
        override val id: String,
        private val authorized: Boolean,
    ) : MusicSource {
        override val displayName = id
        override val capabilities = setOf(SourceCapability.STREAM)

        override suspend fun search(
            query: String,
            limit: Int,
        ): PluginResult<SearchResult> =
            if (authorized) PluginResult.Ok(SearchResult()) else PluginResult.Failure(PluginResult.Kind.UNAUTHORIZED)

        override suspend fun playbackUri(track: TrackRef): PluginResult<String> = PluginResult.Ok("https://x")
    }
}
