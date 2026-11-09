package dev.fuzz.feature.connections

import dev.fuzz.core.domain.connection.ConnectionCheck
import dev.fuzz.core.domain.connection.ConnectionConfig
import dev.fuzz.core.domain.connection.ConnectionKind
import dev.fuzz.core.domain.connection.ConnectionsRepository
import dev.fuzz.core.domain.connection.RequestLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionsViewModelTest {
    private val repository = FakeRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `рабочее подключение сохраняется и форма закрывается`() {
        val vm = ConnectionsViewModel(repository)
        vm.onIntent(ConnectionsIntent.Add(ConnectionKind.Subsonic))
        vm.onIntent(
            ConnectionsIntent.Change {
                it.copy(baseUrl = " https://music.example.ru ", username = "sasha", secret = "pw")
            },
        )
        vm.onIntent(ConnectionsIntent.Save)

        assertNull(vm.state.value.editor)
        val saved = vm.state.value.connections.single()
        assertEquals("music.example.ru", saved.name)
        assertEquals("https://music.example.ru", saved.baseUrl)
        assertEquals("pw", repository.secrets[saved.id])
    }

    @Test
    fun `ошибка проверки остаётся в форме`() {
        repository.next = ConnectionCheck.Unauthorized
        val vm = ConnectionsViewModel(repository)
        vm.onIntent(ConnectionsIntent.Add(ConnectionKind.Jellyfin))
        vm.onIntent(ConnectionsIntent.Change { it.copy(baseUrl = "https://jf.example.ru") })
        vm.onIntent(ConnectionsIntent.Save)

        val editor = vm.state.value.editor!!
        assertEquals(ConnectionCheck.Unauthorized, editor.problem)
        assertTrue(!editor.checking)
        assertTrue(vm.state.value.connections.isEmpty())

        vm.onIntent(ConnectionsIntent.Change { it.copy(secret = "new") })
        assertNull(vm.state.value.editor!!.problem)
    }

    @Test
    fun `правка без нового пароля оставляет старый`() {
        repository.items.value = listOf(config.copy(hasSecret = true))
        repository.secrets[config.id] = "old"
        val vm = ConnectionsViewModel(repository)
        vm.onIntent(ConnectionsIntent.Edit(config.id))
        assertTrue(vm.state.value.editor!!.hasSavedSecret)
        vm.onIntent(ConnectionsIntent.Change { it.copy(name = "Дом") })
        vm.onIntent(ConnectionsIntent.Save)

        assertEquals("old", repository.secrets[config.id])
        assertEquals("Дом", repository.items.value.single().name)
    }

    @Test
    fun `удаление только после подтверждения`() {
        repository.items.value = listOf(config)
        val vm = ConnectionsViewModel(repository)
        vm.onIntent(ConnectionsIntent.AskDelete(config.id))
        assertEquals(config, vm.state.value.confirmDelete)
        vm.onIntent(ConnectionsIntent.CancelDelete)
        assertEquals(1, repository.items.value.size)

        vm.onIntent(ConnectionsIntent.AskDelete(config.id))
        vm.onIntent(ConnectionsIntent.ConfirmDelete)
        assertTrue(repository.items.value.isEmpty())
    }

    @Test
    fun `переключатель включает и выключает`() {
        repository.items.value = listOf(config)
        val vm = ConnectionsViewModel(repository)
        vm.onIntent(ConnectionsIntent.Toggle(config.id, false))
        assertEquals(false, vm.state.value.connections.single().enabled)
    }

    @Test
    fun `JS-плагин сохраняется с описанием и кодом, имя берётся из описания`() {
        val vm = ConnectionsViewModel(repository)
        vm.onIntent(ConnectionsIntent.Add(ConnectionKind.Script))
        assertFalse(vm.state.value.editor!!.canSave)
        vm.onIntent(
            ConnectionsIntent.Change {
                it.copy(manifest = """{"version":1,"id":"demo","name":"Демо"}""", script = "const plugin = {};")
            },
        )
        vm.onIntent(ConnectionsIntent.Save)

        val saved = repository.items.value.single()
        assertEquals("Демо", saved.name)
        assertEquals("const plugin = {};", saved.script)
    }

    @Test
    fun `кнопка «Проверить» показывает итог и журнал`() {
        repository.items.value = listOf(config)
        val vm = ConnectionsViewModel(repository)
        vm.onIntent(ConnectionsIntent.Edit(config.id))
        assertTrue(vm.state.value.editor!!.journal.isEmpty())

        vm.onIntent(ConnectionsIntent.Test)
        val editor = vm.state.value.editor!!
        assertEquals(ConnectionCheck.Ok, editor.testResult)
        assertEquals(1, editor.journal.size)
        assertFalse(editor.testing)
    }

    private val config =
        ConnectionConfig("c1", ConnectionKind.Subsonic, "Navidrome", "https://nd.example.ru", username = "sasha")

    private class FakeRepository : ConnectionsRepository {
        val items = MutableStateFlow<List<ConnectionConfig>>(emptyList())
        val secrets = mutableMapOf<String, String>()
        var next: ConnectionCheck = ConnectionCheck.Ok

        override val connections = items

        override suspend fun save(
            config: ConnectionConfig,
            secret: String?,
        ): ConnectionCheck {
            if (next != ConnectionCheck.Ok) return next
            if (secret != null) secrets[config.id] = secret
            items.update { list ->
                list.filterNot { it.id == config.id } + config.copy(hasSecret = config.id in secrets)
            }
            return next
        }

        override suspend fun setEnabled(
            id: String,
            enabled: Boolean,
        ) = items.update { list -> list.map { if (it.id == id) it.copy(enabled = enabled) else it } }

        override suspend fun remove(id: String) = items.update { list -> list.filterNot { it.id == id } }

        override suspend fun move(
            id: String,
            offset: Int,
        ) = items.update { list ->
            val from = list.indexOfFirst { it.id == id }
            list.toMutableList().apply { add((from + offset).coerceIn(0, lastIndex), removeAt(from)) }
        }

        var checked = 0

        override suspend fun check(id: String): ConnectionCheck {
            checked++
            return next
        }

        override fun journal(id: String) = List(checked) { RequestLine(it.toLong(), "/search", 200, 10) }
    }
}
