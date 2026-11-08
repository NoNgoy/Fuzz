package dev.fuzz.engine.recommend.model

/** Корзины кандидатов из раздела 4.2 ТЗ. Ползунок задаёт их пропорции. */
enum class Bucket { FAVORITES, UNHEARD, SIMILAR, OPEN_CATALOG, PREVIEW }

/** Через что кандидат связан со вкусом: артист или трек, от которого он найден. */
data class Anchor(
    val kind: Kind,
    val name: String,
) {
    enum class Kind { LIKED_ARTIST, LISTENED_ARTIST, LIKED_TRACK, SEED }
}

/**
 * Кандидат от корзины. [strength] 0..1 — насколько корзина в нём уверена (похожесть артиста, аффинити),
 * [anchor] — для объяснения «похож на Deftones, которых ты лайкнул».
 */
data class Candidate(
    val track: WaveTrack,
    val bucket: Bucket,
    val strength: Float = 0f,
    val anchor: Anchor? = null,
)

/** Слагаемые оценки кандидата: формула из раздела «Движок волны» архитектуры. */
enum class ScoreTerm { TASTE_TAGS, ARTIST, AUDIO, CONTEXT_FIT, NOVELTY, POPULARITY, SOURCE, RECENT, SKIPS, JITTER }

/**
 * Почему трек в волне. Строку для «Почему этот трек?» собирает UI: движок отдаёт структуру.
 * [term] — слагаемое с наибольшим положительным вкладом, [tags] — общие теги со вкусом, если они причина.
 */
data class Reason(
    val bucket: Bucket,
    val term: ScoreTerm,
    val anchor: Anchor? = null,
    val tags: List<String> = emptyList(),
    val bpm: Float? = null,
)

/** Трек, выданный волной, с оценкой и объяснением. */
data class WavePick(
    val track: WaveTrack,
    val score: Float,
    val reason: Reason,
)
