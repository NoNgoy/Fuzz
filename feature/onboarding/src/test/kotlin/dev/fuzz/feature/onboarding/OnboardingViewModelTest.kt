package dev.fuzz.feature.onboarding

import app.cash.turbine.test
import dev.fuzz.core.domain.onboarding.MusicFolder
import dev.fuzz.core.domain.onboarding.OnboardingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import dev.fuzz.core.domain.onboarding.OnboardingState as StoredState

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val music = MusicFolder("Music/", "Музыка")
    private val downloads = MusicFolder("Download/", "Загрузки")
    private val repository = FakeOnboardingRepository(listOf(music, downloads))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `background step is shown only on xiaomi firmware`() {
        val generic = OnboardingViewModel(repository).apply { onIntent(OnboardingIntent.Start(false)) }
        val xiaomi = OnboardingViewModel(repository).apply { onIntent(OnboardingIntent.Start(true)) }

        assertEquals(listOf(OnboardingStep.Permissions, OnboardingStep.Folders), generic.state.value.steps)
        assertEquals(OnboardingStep.entries, xiaomi.state.value.steps)
    }

    @Test
    fun `cannot leave folders step with nothing selected`() {
        val vm = OnboardingViewModel(repository).apply { onIntent(OnboardingIntent.Start(false)) }
        vm.onIntent(OnboardingIntent.Next)
        vm.onIntent(OnboardingIntent.ToggleFolder(music.id))
        vm.onIntent(OnboardingIntent.ToggleFolder(downloads.id))

        vm.onIntent(OnboardingIntent.Next)

        assertFalse(vm.state.value.canContinue)
        assertEquals(OnboardingStep.Folders, vm.state.value.step)
    }

    @Test
    fun `last step saves folders and finishes`() =
        runTest {
            val vm = OnboardingViewModel(repository).apply { onIntent(OnboardingIntent.Start(false)) }
            val extra = MusicFolder("content://tree/primary%3ARock", "Rock")
            vm.onIntent(OnboardingIntent.Next)
            vm.onIntent(OnboardingIntent.ToggleFolder(downloads.id))
            vm.onIntent(OnboardingIntent.AddFolder(extra))

            vm.effects.test {
                vm.onIntent(OnboardingIntent.Next)
                assertEquals(OnboardingEffect.Finished, awaitItem())
            }
            val stored = repository.value
            assertTrue(stored.completed)
            assertEquals(listOf(music, extra), stored.folders)
        }

    private class FakeOnboardingRepository(
        defaults: List<MusicFolder>,
    ) : OnboardingRepository {
        private val flow = MutableStateFlow(StoredState(completed = false, folders = defaults))
        val value: StoredState get() = flow.value
        override val state = flow

        override suspend fun setFolders(folders: List<MusicFolder>) = flow.update { it.copy(folders = folders) }

        override suspend fun complete() = flow.update { it.copy(completed = true) }
    }
}
