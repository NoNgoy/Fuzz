package dev.fuzz.core.domain.wave

import dev.fuzz.core.model.wave.WaveSeed
import dev.fuzz.core.model.wave.WaveSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Настройки волны хранятся между запусками: ползунок, занятие, настроение, характер. */
interface WaveSettingsRepository {
    val settings: Flow<WaveSettings>

    suspend fun update(transform: (WaveSettings) -> WaveSettings)
}

/** Корзины волны в терминах экрана: для полоски пропорций под ползунком. */
enum class WaveSource { Favorites, Unheard, Similar, OpenCatalog, Preview }

data class WaveShare(
    val source: WaveSource,
    /** Доля 0..1, сумма по всем корзинам равна единице. */
    val share: Float,
)

data class WaveStatus(
    /** Сейчас играет очередь, собранная волной. */
    val active: Boolean = false,
    val starting: Boolean = false,
    val seed: WaveSeed? = null,
)

sealed interface WaveStartResult {
    data object Started : WaveStartResult

    data object EmptyLibrary : WaveStartResult
}

/**
 * Волна поверх плеера: собирает первую пачку, доливает очередь, когда впереди остаётся мало треков,
 * и пересобирает несыгранный остаток после пропуска, лайка, дизлайка или смены настроек.
 */
interface WavePlayer {
    val status: StateFlow<WaveStatus>

    suspend fun start(seed: WaveSeed = WaveSeed.MyWave): WaveStartResult

    /** Пропорции корзин при таком положении ползунка с учётом доступных подключений. */
    fun mix(familiarity: Int): List<WaveShare>
}
