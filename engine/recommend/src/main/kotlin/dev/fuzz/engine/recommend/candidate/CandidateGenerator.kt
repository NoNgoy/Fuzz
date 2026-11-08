package dev.fuzz.engine.recommend.candidate

import dev.fuzz.core.model.TrackId
import dev.fuzz.core.model.wave.WaveSeed
import dev.fuzz.core.model.wave.WaveSettings
import dev.fuzz.engine.recommend.model.Bucket
import dev.fuzz.engine.recommend.model.Candidate
import dev.fuzz.engine.recommend.profile.TasteProfile

/**
 * Корзина кандидатов волны (раздел 8.2 ТЗ). Встроенные корзины живут здесь, свои API из раздела 8.4
 * подключаются тем же интерфейсом. Корзина не должна возвращать треки из [WaveContext.exclude]
 * (уже выдано или дизлайкнуто); треки из [WaveContext.pooled] уже лежат в пуле, их стоит
 * возвращать, только если корзина может их объяснить лучше. [WaveContext.round] растёт при каждой доливке — корзина
 * копает глубже (следующие артисты графа, следующая страница каталога), поэтому поток не кончается.
 */
interface CandidateGenerator {
    val bucket: Bucket

    /** Отвечает из локальной базы за десятки миллисекунд; такие корзины собирают первую пачку. */
    val isLocal: Boolean get() = false

    suspend fun candidates(
        ctx: WaveContext,
        limit: Int,
    ): List<Candidate>
}

/** Всё, что корзина знает о текущей волне. */
class WaveContext(
    val seed: WaveSeed,
    val settings: WaveSettings,
    val profile: TasteProfile,
    val library: LibraryIndex,
    val seedInfo: SeedInfo,
    val round: Int,
    val exclude: Set<TrackId>,
    val pooled: Set<TrackId>,
    /** Расширение графа похожести на сессию; null, если провайдеров похожести нет. */
    val explorer: SimilarityExplorer?,
)
