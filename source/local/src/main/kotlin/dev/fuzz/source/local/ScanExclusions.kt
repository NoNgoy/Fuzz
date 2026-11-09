package dev.fuzz.source.local

import android.os.Build
import android.provider.MediaStore
import dev.fuzz.core.domain.settings.ScanRules

/**
 * Что сканер пропускает: короткие файлы (рингтоны, звуки) и записи — диктофон, звонки, голосовые
 * мессенджеров. Записи узнаются по папкам, куда их кладут Android, MIUI/HyperOS, Samsung и мессенджеры,
 * а с Android 12 ещё и по флагу MediaStore.
 */
internal data class ScanExclusions(
    /** Начинается с « AND », чтобы дописываться к основному условию; пустая — без исключений. */
    val selection: String,
    val args: List<String>,
) {
    companion object {
        val VOICE_FOLDERS =
            listOf(
                "Recordings/",
                "Voice Recorder/",
                "VoiceRecorder/",
                "Call/",
                "Call recordings/",
                "CallRecordings/",
                "MIUI/sound_recorder/",
                "WhatsApp Voice Notes/",
                "WhatsApp Audio/",
                "Telegram Audio/",
            )

        fun of(rules: ScanRules): ScanExclusions {
            val clauses = mutableListOf<String>()
            val args = mutableListOf<String>()
            if (rules.minDurationSec > 0) {
                clauses += "${MediaStore.Audio.Media.DURATION} >= ?"
                args += (rules.minDurationSec * MS_PER_SECOND).toString()
            }
            if (rules.skipVoiceNotes) {
                VOICE_FOLDERS.forEach { folder ->
                    clauses += "${MediaStore.Audio.Media.RELATIVE_PATH} NOT LIKE ?"
                    args += "%$folder%"
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    clauses += "${MediaStore.Audio.Media.IS_RECORDING} = 0"
                }
            }
            return ScanExclusions(clauses.joinToString("") { " AND $it" }, args)
        }

        private const val MS_PER_SECOND = 1_000L
    }
}
