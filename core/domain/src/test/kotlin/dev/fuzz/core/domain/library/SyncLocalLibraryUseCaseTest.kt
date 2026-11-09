package dev.fuzz.core.domain.library

import dev.fuzz.core.domain.onboarding.MusicFolder
import dev.fuzz.core.domain.onboarding.OnboardingRepository
import dev.fuzz.core.domain.onboarding.OnboardingState
import dev.fuzz.core.domain.settings.AppSettings
import dev.fuzz.core.domain.settings.AppSettingsRepository
import dev.fuzz.core.domain.settings.ScanRules
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SyncLocalLibraryUseCaseTest {
    private val music = MusicFolder("Music/", "Музыка")
    private val scanner = mockk<LocalMusicScanner>()
    private val store = mockk<LocalLibraryStore>()
    private val rules = ScanRules(skipVoiceNotes = true, minDurationSec = 15)

    private fun useCase(folders: List<MusicFolder>): SyncLocalLibraryUseCase {
        val onboarding = mockk<OnboardingRepository>()
        every { onboarding.state } returns flowOf(OnboardingState(completed = true, folders = folders))
        val settings = mockk<AppSettingsRepository>()
        every { settings.settings } returns flowOf(AppSettings(scan = rules))
        return SyncLocalLibraryUseCase(scanner, onboarding, store, settings)
    }

    @Test
    fun `without permission the collection is left untouched`() =
        runTest {
            coEvery { scanner.scan(any(), any()) } returns ScanResult.NoPermission

            assertEquals(SyncOutcome.NoPermission, useCase(listOf(music))())
            coVerify(exactly = 0) { store.replaceLocal(any()) }
        }

    @Test
    fun `no folders means no scan`() =
        runTest {
            assertEquals(SyncOutcome.NoFolders, useCase(emptyList())())
            coVerify(exactly = 0) { scanner.scan(any(), any()) }
        }

    @Test
    fun `scanned tracks go to the store`() =
        runTest {
            val stats = SyncStats(added = 1, updated = 0, removed = 0)
            coEvery { scanner.scan(listOf(music), rules) } returns ScanResult.Success(emptyList())
            coEvery { store.replaceLocal(emptyList()) } returns stats

            assertEquals(SyncOutcome.Synced(stats), useCase(listOf(music))())
        }
}
