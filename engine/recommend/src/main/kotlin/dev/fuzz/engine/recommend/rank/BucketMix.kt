package dev.fuzz.engine.recommend.rank

import dev.fuzz.engine.recommend.config.MixAnchor
import dev.fuzz.engine.recommend.model.Bucket

/** Пропорции корзин по положению ползунка: линейная интерполяция между опорными точками конфига. */
object BucketMix {
    fun shares(
        anchors: List<MixAnchor>,
        familiarity: Int,
    ): Map<Bucket, Float> {
        require(anchors.isNotEmpty()) { "В конфиге волны нет пропорций корзин (mix)" }
        val sorted = anchors.sortedBy { it.familiarity }
        val upperIndex = sorted.indexOfFirst { it.familiarity >= familiarity }
        val raw =
            when {
                upperIndex == -1 -> sorted.last().shares
                upperIndex == 0 -> sorted.first().shares
                else -> lerp(sorted[upperIndex - 1], sorted[upperIndex], familiarity)
            }
        return normalize(raw)
    }

    /** Убрать корзины, которым нечего дать (нет сети, нет плагина), и раздать их долю остальным. */
    fun redistribute(
        shares: Map<Bucket, Float>,
        available: Set<Bucket>,
    ): Map<Bucket, Float> {
        val kept = shares.filterKeys { it in available }
        return if (kept.values.sum() > 0f) normalize(kept) else available.associateWith { 1f / available.size }
    }

    private fun lerp(
        lo: MixAnchor,
        hi: MixAnchor,
        x: Int,
    ): Map<Bucket, Float> {
        val t = (x - lo.familiarity).toFloat() / (hi.familiarity - lo.familiarity)
        return Bucket.entries.associateWith { b ->
            val a = lo.shares[b] ?: 0f
            a + ((hi.shares[b] ?: 0f) - a) * t
        }
    }

    private fun normalize(shares: Map<Bucket, Float>): Map<Bucket, Float> {
        val sum = shares.values.sum()
        return if (sum <= 0f) shares else shares.mapValues { it.value / sum }.filterValues { it > 0f }
    }
}
