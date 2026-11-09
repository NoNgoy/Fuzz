package dev.fuzz.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.fuzz.core.data.connection.DefaultConnectionsRepository
import dev.fuzz.core.data.connection.KeystoreSecretCipher
import dev.fuzz.core.data.connection.SecretCipher
import dev.fuzz.core.data.importer.EnginePlaylistMatcher
import dev.fuzz.core.data.library.OfflineLibraryRepository
import dev.fuzz.core.data.lyrics.ProviderLyricsRepository
import dev.fuzz.core.data.onboarding.DataStoreOnboardingRepository
import dev.fuzz.core.data.player.DataStoreSoundSettingsRepository
import dev.fuzz.core.data.player.DefaultSleepTimer
import dev.fuzz.core.data.playlist.OfflinePlaylistRepository
import dev.fuzz.core.data.reaction.OfflineReactionRepository
import dev.fuzz.core.data.search.PluginOnlineSearch
import dev.fuzz.core.data.sync.RoomLocalLibraryStore
import dev.fuzz.core.data.wave.DataStoreWaveSettingsRepository
import dev.fuzz.core.data.wave.ExternalTrackRegistry
import dev.fuzz.core.data.wave.RoomWaveLibrary
import dev.fuzz.core.data.wave.WaveRunner
import dev.fuzz.core.domain.connection.ConnectedSources
import dev.fuzz.core.domain.connection.ConnectionsRepository
import dev.fuzz.core.domain.importer.PlaylistMatcher
import dev.fuzz.core.domain.library.LibraryRepository
import dev.fuzz.core.domain.library.LocalLibraryStore
import dev.fuzz.core.domain.lyrics.LyricsRepository
import dev.fuzz.core.domain.onboarding.OnboardingRepository
import dev.fuzz.core.domain.player.PlaybackHeaders
import dev.fuzz.core.domain.player.SleepTimer
import dev.fuzz.core.domain.player.SoundSettingsRepository
import dev.fuzz.core.domain.playlist.PlaylistRepository
import dev.fuzz.core.domain.reaction.ReactionRepository
import dev.fuzz.core.domain.search.OnlineSearch
import dev.fuzz.core.domain.wave.WavePlayer
import dev.fuzz.core.domain.wave.WaveSettingsRepository
import dev.fuzz.engine.recommend.port.WaveLibrary
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
    @Singleton
    fun bindWaveSettingsRepository(impl: DataStoreWaveSettingsRepository): WaveSettingsRepository

    @Binds
    fun bindSoundSettings(impl: DataStoreSoundSettingsRepository): SoundSettingsRepository

    @Binds
    fun bindSleepTimer(impl: DefaultSleepTimer): SleepTimer

    @Binds
    fun bindWaveLibrary(impl: RoomWaveLibrary): WaveLibrary

    @Binds
    fun bindWavePlayer(impl: WaveRunner): WavePlayer

    @Binds
    fun bindPlaylistMatcher(impl: EnginePlaylistMatcher): PlaylistMatcher

    @Binds
    fun bindConnectionsRepository(impl: DefaultConnectionsRepository): ConnectionsRepository

    @Binds
    fun bindConnectedSources(impl: DefaultConnectionsRepository): ConnectedSources

    @Binds
    fun bindSecretCipher(impl: KeystoreSecretCipher): SecretCipher

    @Binds
    fun bindLyricsRepository(impl: ProviderLyricsRepository): LyricsRepository

    @Binds
    fun bindOnlineSearch(impl: PluginOnlineSearch): OnlineSearch

    @Binds
    fun bindPlaybackHeaders(impl: ExternalTrackRegistry): PlaybackHeaders
}
