package dev.fuzz.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.fuzz.engine.recommend.WaveEngine
import dev.fuzz.engine.recommend.port.ExternalCatalog
import dev.fuzz.engine.recommend.port.SimilarityGraph
import dev.fuzz.engine.recommend.port.WaveLibrary
import javax.inject.Singleton

/** Движок волны из портов. Граф похожести и внешние каталоги собирает app из подключённых плагинов. */
@Module
@InstallIn(SingletonComponent::class)
internal object WaveModule {
    @Provides
    @Singleton
    fun provideWaveEngine(
        library: WaveLibrary,
        graph: SimilarityGraph,
        catalogs: List<@JvmSuppressWildcards ExternalCatalog>,
    ): WaveEngine = WaveEngine(library, graph, catalogs)
}
