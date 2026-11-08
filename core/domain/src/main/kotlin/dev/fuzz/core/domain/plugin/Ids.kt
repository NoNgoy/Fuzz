package dev.fuzz.core.domain.plugin

/** Внутренний ID трека/альбома/артиста Fuzz. Не зависит от источника. */
@JvmInline value class FuzzId(val value: String)

/** Связка с внешним сервисом: один трек из разных источников склеивается по этим ID. */
data class ExternalId(val provider: String, val id: String)

object Providers {
    const val MUSICBRAINZ = "musicbrainz"
    const val DEEZER = "deezer"
    const val JAMENDO = "jamendo"
    const val LASTFM = "lastfm"
    const val SUBSONIC = "subsonic"
    const val LOCAL = "local"
}
