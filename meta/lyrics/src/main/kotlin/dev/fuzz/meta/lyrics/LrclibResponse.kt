package dev.fuzz.meta.lyrics

import kotlinx.serialization.Serializable

@Serializable
internal data class LrclibResponse(
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null,
)
