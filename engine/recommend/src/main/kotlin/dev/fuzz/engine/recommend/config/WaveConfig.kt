@file:Suppress("MagicNumber") // значения по умолчанию повторяют wave-config.json

package dev.fuzz.engine.recommend.config

import dev.fuzz.core.model.wave.WaveActivity
import dev.fuzz.core.model.wave.WaveCharacter
import dev.fuzz.core.model.wave.WaveMood
import dev.fuzz.engine.recommend.model.Bucket
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Все настройки алгоритма волны. Значения по умолчанию лежат в `wave-config.json` рядом с классом,
 * подбираются на симуляции sim:wave и меняются без правок кода. [version] растёт при смене смысла полей.
 */
@Serializable
data class WaveConfig(
    val version: Int = 1,
    val batchSize: Int = 10,
    val weights: ScoreWeights = ScoreWeights(),
    val profile: ProfileConfig = ProfileConfig(),
    val mix: List<MixAnchor> = emptyList(),
    val characters: Map<WaveCharacter, CharacterPreset> = emptyMap(),
    val activities: Map<WaveActivity, ContextRange> = emptyMap(),
    val moods: Map<WaveMood, ContextRange> = emptyMap(),
    val assembly: AssemblyConfig = AssemblyConfig(),
    val pool: PoolConfig = PoolConfig(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): WaveConfig = json.decodeFromString(serializer(), text)

        /** Конфиг из ресурсов модуля. */
        val Default: WaveConfig by lazy {
            val stream =
                requireNotNull(WaveConfig::class.java.getResourceAsStream("wave-config.json")) {
                    "wave-config.json не найден в ресурсах engine:recommend"
                }
            parse(stream.bufferedReader().use { it.readText() })
        }
    }
}

/** Веса слагаемых формулы оценки. */
@Serializable
data class ScoreWeights(
    val tasteTags: Float = 1f,
    val artist: Float = 0.8f,
    val audio: Float = 0.4f,
    val contextFit: Float = 1.5f,
    /** Вес новизны при ползунке 100; при 0 он такой же, но со знаком минус (тянет к знакомому). */
    val novelty: Float = 0.6f,
    val popularity: Float = 0.15f,
    val source: Float = 0.7f,
    val recent: Float = 1.2f,
    val skips: Float = 1f,
    val jitter: Float = 0.25f,
)

/** Как копится профиль вкуса: веса событий и затухание. */
@Serializable
data class ProfileConfig(
    val like: Float = 3f,
    val completed: Float = 1f,
    val addedToPlaylist: Float = 2f,
    val earlySkip: Float = -1f,
    val dislike: Float = -3f,
    /** Пропуск позже этой секунды считается слабым положительным сигналом, раньше — отрицательным. */
    val earlySkipMs: Long = 30_000,
    val lateSkip: Float = 0.3f,
    val halfLifeDays: Float = 30f,
    /** Холодный старт: каждый трек, положенный в коллекцию, — слабый лайк. */
    val collectionPrior: Float = 0.3f,
    /** Во сколько раз реакция внутри сессии сильнее старой истории. */
    val sessionBoost: Float = 3f,
    /** Полупериод штрафа «недавно играл», в часах. */
    val recentHalfLifeHours: Float = 72f,
)

/** Пропорции корзин в точке ползунка; между точками интерполяция. */
@Serializable
data class MixAnchor(
    val familiarity: Int,
    val shares: Map<Bucket, Float>,
)

/** Пресет характера: заменяет положение ползунка и может усилить популярность. */
@Serializable
data class CharacterPreset(
    val familiarity: Int,
    val popularityWeight: Float? = null,
)

/** Диапазоны и теги занятия или настроения. Пустой диапазон (null) не проверяется. */
@Serializable
data class ContextRange(
    val bpmMin: Float? = null,
    val bpmMax: Float? = null,
    val energyMin: Float? = null,
    val energyMax: Float? = null,
    val valenceMin: Float? = null,
    val valenceMax: Float? = null,
    val tags: List<String> = emptyList(),
    /** Теги, которые точно не подходят (метал перед сном). */
    val avoidTags: List<String> = emptyList(),
)

/** Сборка пачки: MMR, переходы, ограничения по артисту. */
@Serializable
data class AssemblyConfig(
    val mmrLambda: Float = 0.6f,
    val similarityWindow: Int = 5,
    val maxSameArtistInRow: Int = 2,
    val maxSameArtistPerBatch: Int = 3,
    val bpmJumpScale: Float = 40f,
    val energyJumpScale: Float = 0.5f,
    val transitionWeight: Float = 0.4f,
)

/** Пул кандидатов сессии. */
@Serializable
data class PoolConfig(
    val targetSize: Int = 1000,
    val perGeneratorLimit: Int = 200,
    /** Если подходящих кандидатов корзины меньше batchSize × это число, корзина доливается. */
    val refillFactor: Int = 3,
    val startTimeoutMs: Long = 600,
    val generatorTimeoutMs: Long = 5_000,
    /** Сколько последних треков сессии нельзя повторять, когда пул совсем иссяк. */
    val minRepeatGap: Int = 150,
)
