package dev.fuzz.core.domain.wave

/** Что сильнее всего подняло трек в волне. */
enum class WaveFactor { Tags, Artist, Sound, Mood, Novelty, Popularity, Link, Chance }

/** Через что трек связан со вкусом: артист или трек, от которого волна его нашла. */
data class WaveAnchor(
    val kind: Kind,
    val name: String,
) {
    enum class Kind { LikedArtist, ListenedArtist, LikedTrack, Seed }
}

/** Ответ на «Почему этот трек?». Текст собирает экран. */
data class WaveReason(
    val source: WaveSource,
    val factor: WaveFactor,
    val anchor: WaveAnchor? = null,
    /** Общие теги со вкусом или с тем, от чего начата волна, не больше трёх. */
    val tags: List<String> = emptyList(),
    /** Темп, если трек выбран под занятие. */
    val bpm: Int? = null,
)
