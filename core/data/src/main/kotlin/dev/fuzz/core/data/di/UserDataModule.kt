package dev.fuzz.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.fuzz.core.data.artist.DefaultArtistPages
import dev.fuzz.core.data.library.RoomCollectionEditor
import dev.fuzz.core.data.settings.DataStoreAppearanceRepository
import dev.fuzz.core.data.stats.RoomListeningStats
import dev.fuzz.core.domain.artist.ArtistPages
import dev.fuzz.core.domain.library.CollectionEditor
import dev.fuzz.core.domain.settings.AppearanceRepository
import dev.fuzz.core.domain.stats.ListeningStatsRepository

/** То, что человек правит в настройках и профиле: оформление, статистика, ручные правки коллекции. */
@Module
@InstallIn(SingletonComponent::class)
internal interface UserDataModule {
    @Binds
    fun bindListeningStats(impl: RoomListeningStats): ListeningStatsRepository

    @Binds
    fun bindAppearance(impl: DataStoreAppearanceRepository): AppearanceRepository

    @Binds
    fun bindArtistPages(impl: DefaultArtistPages): ArtistPages

    @Binds
    fun bindCollectionEditor(impl: RoomCollectionEditor): CollectionEditor
}
