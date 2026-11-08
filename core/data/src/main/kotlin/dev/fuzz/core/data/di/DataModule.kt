package dev.fuzz.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.fuzz.core.data.importer.EnginePlaylistMatcher
import dev.fuzz.core.data.library.OfflineLibraryRepository
import dev.fuzz.core.data.onboarding.DataStoreOnboardingRepository
import dev.fuzz.core.data.playlist.OfflinePlaylistRepository
import dev.fuzz.core.data.reaction.OfflineReactionRepository
import dev.fuzz.core.data.sync.RoomLocalLibraryStore
import dev.fuzz.core.domain.importer.PlaylistMatcher
import dev.fuzz.core.domain.library.LibraryRepository
import dev.fuzz.core.domain.library.LocalLibraryStore
import dev.fuzz.core.domain.onboarding.OnboardingRepository
import dev.fuzz.core.domain.playlist.PlaylistRepository
import dev.fuzz.core.domain.reaction.ReactionRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {
    @Binds
    @Singleton
    fun bindLibraryRepository(impl: OfflineLibraryRepository): LibraryRepository

    @Binds
    @Singleton
    fun bindReactionRepository(impl: OfflineReactionRepository): ReactionRepository

    @Binds
    @Singleton
    fun bindOnboardingRepository(impl: DataStoreOnboardingRepository): OnboardingRepository

    @Binds
    @Singleton
    fun bindLocalLibraryStore(impl: RoomLocalLibraryStore): LocalLibraryStore

    @Binds
    @Singleton
    fun bindPlaylistRepository(impl: OfflinePlaylistRepository): PlaylistRepository

    @Binds
    fun bindPlaylistMatcher(impl: EnginePlaylistMatcher): PlaylistMatcher
}
