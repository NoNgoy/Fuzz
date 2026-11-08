package dev.fuzz.feature.onboarding

import dev.fuzz.core.domain.onboarding.MusicFolder
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
class OnboardingScreenshotTest {
    private val actions = OnboardingActions({}, {}, {}, {}, {}, {})
    private val denied = SystemStatus(audioGranted = false, notificationsGranted = false, batteryUnrestricted = false)
    private val xiaomi =
        OnboardingState(
            showBackgroundStep = true,
            folders =
                persistentListOf(
                    FolderOption(MusicFolder("Music/", "Музыка"), selected = true),
                    FolderOption(MusicFolder("Download/", "Загрузки"), selected = true),
                    FolderOption(MusicFolder("SD/Rock/", "Rock"), selected = false),
                ),
        )

    @Test
    fun permissions() =
        captureThemedScreens("onboarding_permissions") {
            OnboardingScreen(state = xiaomi, system = denied, actions = actions)
        }

    @Test
    fun folders() =
        captureThemedScreens("onboarding_folders") {
            OnboardingScreen(
                state = xiaomi.copy(step = OnboardingStep.Folders),
                system = denied.copy(audioGranted = true),
                actions = actions,
            )
        }

    @Test
    fun background() =
        captureThemedScreens("onboarding_background") {
            OnboardingScreen(
                state = xiaomi.copy(step = OnboardingStep.Background),
                system = denied.copy(audioGranted = true, notificationsGranted = true),
                actions = actions,
            )
        }
}
