package dev.fuzz.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.fuzz.core.domain.plugin.PlaylistImporter
import dev.fuzz.importer.formats.CsvImporter
import dev.fuzz.importer.formats.M3uImporter
import dev.fuzz.importer.formats.TextImporter
import dev.fuzz.importer.formats.XspfImporter

/** Форматы импорта плейлистов. Новый формат — ещё один @IntoSet. */
@Module
@InstallIn(SingletonComponent::class)
object ImportersModule {
    @Provides
    @IntoSet
    fun m3u(): PlaylistImporter = M3uImporter()

    @Provides
    @IntoSet
    fun csv(): PlaylistImporter = CsvImporter()

    @Provides
    @IntoSet
    fun xspf(): PlaylistImporter = XspfImporter()

    @Provides
    @IntoSet
    fun text(): PlaylistImporter = TextImporter()
}
