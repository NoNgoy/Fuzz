package dev.fuzz.feature.connections

import dev.fuzz.core.domain.connection.ConnectionCheck
import dev.fuzz.core.domain.connection.ConnectionConfig
import dev.fuzz.core.domain.connection.ConnectionKind
import dev.fuzz.core.domain.connection.RequestLine
import dev.fuzz.core.testing.captureThemedScreens
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h873dp-440dpi")
class ConnectionsScreenshotTest {
    private val list =
        persistentListOf(
            ConnectionConfig(
                "1",
                ConnectionKind.Subsonic,
                "Navidrome дома",
                "https://music.home.ru",
                "sasha",
                hasSecret = true,
            ),
            ConnectionConfig(
                "2",
                ConnectionKind.WebDav,
                "Nextcloud",
                "https://cloud.ru/dav/Music",
                "sasha",
                enabled = false,
            ),
        )

    @Test
    fun empty() = captureThemedScreens("connections_empty") { ConnectionsScreen(ConnectionsState(), {}, {}) }

    @Test
    fun list() = captureThemedScreens("connections_list") { ConnectionsScreen(ConnectionsState(list), {}, {}) }

    @Test
    fun editorError() =
        captureThemedScreens("connections_editor_error") {
            ConnectionsScreen(
                ConnectionsState(
                    list,
                    editor =
                        ConnectionEditor(
                            ConnectionKind.Subsonic,
                            baseUrl = "https://music.home.ru",
                            username = "sasha",
                            secret = "123",
                            problem = ConnectionCheck.Unauthorized,
                        ),
                ),
                {},
                {},
            )
        }

    @Test
    fun manifestTested() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
        captureThemedScreens("connections_manifest_journal") {
            ConnectionsScreen(
                ConnectionsState(
                    list,
                    editor =
                        ConnectionEditor(
                            ConnectionKind.Manifest,
                            id = "3",
                            name = "Мой API",
                            manifest = """{"version":1,"name":"Мой API","baseUrl":"https://api.example.ru"}""",
                            hasSavedSecret = true,
                            testResult = ConnectionCheck.Unauthorized,
                            journal =
                                persistentListOf(
                                    RequestLine(1_700_000_120_000, "/v1/search?q=love&limit=1", 401, 182),
                                    RequestLine(1_700_000_060_000, "/oauth/token", null, 15_000, "Таймаут"),
                                    RequestLine(1_700_000_000_000, "/v1/search?q=a&limit=1", 200, 95),
                                ),
                        ),
                ),
                {},
                {},
            )
        }
    }

    @Test
    fun scriptEditor() =
        captureThemedScreens("connections_script") {
            ConnectionsScreen(
                ConnectionsState(
                    list,
                    editor =
                        ConnectionEditor(
                            ConnectionKind.Script,
                            manifest = """{"version":1,"id":"demo","name":"Демо","domains":["api.example.ru"]}""",
                            script = "const plugin = {\n  async search(query, limit) { return [] },\n};",
                        ),
                ),
                {},
                {},
            )
        }
}
