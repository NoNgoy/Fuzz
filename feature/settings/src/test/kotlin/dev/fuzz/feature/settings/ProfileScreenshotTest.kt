package dev.fuzz.feature.settings

import dev.fuzz.core.testing.captureThemedScreens
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h873dp-440dpi")
class ProfileScreenshotTest {
    @Test
    fun profile() =
        captureThemedScreens("profile") {
            ProfileScreen(onOpenImport = {}, onOpenConnections = {}, onOpenSettings = {})
        }
}
