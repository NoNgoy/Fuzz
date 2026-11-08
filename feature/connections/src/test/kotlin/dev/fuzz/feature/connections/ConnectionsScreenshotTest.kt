package dev.fuzz.feature.connections

import dev.fuzz.core.domain.connection.ConnectionCheck
import dev.fuzz.core.domain.connection.ConnectionConfig
import dev.fuzz.core.domain.connection.ConnectionKind
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
}
