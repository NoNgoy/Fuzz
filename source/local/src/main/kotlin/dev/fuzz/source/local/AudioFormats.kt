package dev.fuzz.source.local

/** Метка формата для списка треков по MIME-типу из MediaStore. */
internal fun formatLabel(mimeType: String?): String? =
    when (mimeType?.lowercase()) {
        null -> null
        "audio/flac", "audio/x-flac" -> "FLAC"
        "audio/mpeg", "audio/mp3" -> "MP3"
        "audio/mp4", "audio/m4a", "audio/x-m4a", "audio/aac", "audio/aacp" -> "AAC"
        "audio/alac" -> "ALAC"
        "audio/ogg", "application/ogg", "audio/vorbis" -> "OGG"
        "audio/opus" -> "OPUS"
        "audio/wav", "audio/x-wav", "audio/wave" -> "WAV"
        "audio/x-ms-wma" -> "WMA"
        "audio/x-ape", "audio/ape" -> "APE"
        "audio/x-dsf", "audio/dsf" -> "DSD"
        else -> mimeType.substringAfter('/').uppercase().takeIf { it.length <= MAX_LABEL_LENGTH }
    }

/** В MediaStore номер трека хранится как диск × 1000 + номер. */
internal fun trackNumberOf(raw: Int): Int? = (raw % DISC_MULTIPLIER).takeIf { it > 0 }

/** MediaStore пишет «<unknown>», когда тега нет. */
internal fun String?.tagOrNull(): String? = this?.trim()?.takeUnless { it.isEmpty() || it == "<unknown>" }

private const val MAX_LABEL_LENGTH = 5
private const val DISC_MULTIPLIER = 1000
