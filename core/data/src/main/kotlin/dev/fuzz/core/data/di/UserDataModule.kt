package dev.fuzz.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.fuzz.core.data.artist.DefaultArtistPages
import dev.fuzz.core.data.backup.FileBackupRepository
import dev.fuzz.core.data.library.RoomCollectionEditor
import dev.fuzz.core.data.lyrics.LyricsStore
import dev.fuzz.core.data.lyrics.RoomLyricsStore
import dev.fuzz.core.data.online.FileDownloads
import dev.fuzz.core.data.online.SavedOnlineTracks
import dev.fuzz.core.data.settings.DataStoreAppearanceRepository
import dev.fuzz.core.data.stats.RoomListeningStats
import dev.fuzz.core.data.stats.RoomYearRecap
import dev.fuzz.core.data.wishlist.RoomWishlist
import dev.fuzz.core.domain.artist.ArtistPages
import dev.fuzz.core.domain.backup.BackupRepository
import dev.fuzz.core.domain.download.Downloads
import dev.fuzz.core.domain.library.CollectionEditor
import dev.fuzz.core.domain.player.SavedStreams
import dev.fuzz.core.domain.settings.AppearanceRepository
import dev.fuzz.core.domain.stats.ListeningStatsRepository
import dev.fuzz.core.domain.stats.YearRecapRepository
import dev.fuzz.core.domain.wishlist.WishlistRepository

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

    @Binds
    fun bindSavedStreams(impl: SavedOnlineTracks): SavedStreams

    @Binds
    fun bindDownloads(impl: FileDownloads): Downloads

    @Binds
    fun bindBackup(impl: FileBackupRepository): BackupRepository

    @Binds
    fun bindWishlist(impl: RoomWishlist): WishlistRepository

    @Binds
    fun bindYearRecap(impl: RoomYearRecap): YearRecapRepository

    @Binds
    fun bindLyricsStore(impl: RoomLyricsStore): LyricsStore
}
