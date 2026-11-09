package dev.fuzz.engine.recommend

import dev.fuzz.core.model.wave.WaveSeed
import dev.fuzz.core.model.wave.WaveSettings
import dev.fuzz.engine.recommend.candidate.ArtistCatalogGenerator
import dev.fuzz.engine.recommend.candidate.CandidateGenerator
import dev.fuzz.engine.recommend.candidate.FavoritesGenerator
import dev.fuzz.engine.recommend.candidate.OpenCatalogGenerator
import dev.fuzz.engine.recommend.candidate.PreviewGenerator
import dev.fuzz.engine.recommend.candidate.SimilarGenerator
import dev.fuzz.engine.recommend.candidate.UnheardGenerator
import dev.fuzz.engine.recommend.candidate.WaveContext
import dev.fuzz.engine.recommend.config.WaveConfig
import dev.fuzz.engine.recommend.model.TrackOrigin
import dev.fuzz.engine.recommend.model.WaveSignal
import dev.fuzz.engine.recommend.model.WaveTrack
import dev.fuzz.engine.recommend.port.ExternalCatalog
import dev.fuzz.engine.recommend.port.SimilarityGraph
import dev.fuzz.engine.recommend.port.WaveLibrary
import dev.fuzz.engine.recommend.profile.TasteProfile
import dev.fuzz.engine.recommend.profile.TasteProfileBuilder

/**
 * Точка входа в движок волны. core:data собирает его из портов (Room, meta:*, source:*) и
 * создаёт [WaveSession] на каждый запуск волны. Сам движок не хранит состояния между сессиями:
 * история и профиль вкуса живут в базе.
 */
class WaveEngine(
    private val library: WaveLibrary,
    private val graph: SimilarityGraph? = null,
    catalogs: List<ExternalCatalog> = emptyList(),
    extraGenerators: List<CandidateGenerator> = emptyList(),
    private val config: WaveConfig = WaveConfig.Default,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val generators: List<CandidateGenerator> =
        buildList {
            add(FavoritesGenerator())
            add(UnheardGenerator())
            add(SimilarGenerator())
            catalogs.forEach { catalog ->
                when (catalog.origin) {
                    TrackOrigin.Preview -> {
                        add(PreviewGenerator(catalog))
                    }

                    TrackOrigin.OpenCatalog -> {
                        add(
                            if (catalog.searchesByTags) {
                                OpenCatalogGenerator(catalog, ::contextTags)
                            } else {
                                ArtistCatalogGenerator(catalog)
                            },
                        )
                    }

                    TrackOrigin.Collection -> {}
                }
            }
            addAll(extraGenerators)
        }

    fun session(
        seed: WaveSeed,
        settings: WaveSettings,
        profile: TasteProfile,
        randomSeed: Long = clock(),
    ): WaveSession = WaveSession(seed, settings, profile, library, graph, generators, config, clock, randomSeed)

    /**
     * Профиль из всей истории (ночная пересборка или первый запуск). Если истории нет совсем,
     * вкус берётся из самой коллекции (холодный старт).
     */
    fun buildProfile(
        collection: List<WaveTrack>,
        signals: List<WaveSignal>,
        nowMs: Long = clock(),
    ): TasteProfile {
        val byId = collection.associateBy { it.id }
        val builder = TasteProfileBuilder(config.profile, nowMs) { byId[it] }
        builder.addCollectionPrior(collection)
        return builder.addAll(signals).build()
    }

    private fun contextTags(ctx: WaveContext): List<String> =
        listOfNotNull(
            ctx.settings.activity?.let { config.activities[it] },
            ctx.settings.mood?.let { config.moods[it] },
        ).flatMap { it.tags }
}
