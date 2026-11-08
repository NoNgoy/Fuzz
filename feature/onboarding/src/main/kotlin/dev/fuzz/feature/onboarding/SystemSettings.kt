package dev.fuzz.feature.onboarding

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.DocumentsContract
import android.provider.Settings

/** Прошивки Xiaomi (MIUI и HyperOS) закрывают фоновые приложения без автозапуска. */
internal fun isXiaomiFirmware(): Boolean =
    Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco")

internal fun Context.isIgnoringBatteryOptimizations(): Boolean =
    getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

/** Экран автозапуска в «Безопасности». Если его нет, открываем карточку приложения. */
internal fun Context.openAutostartSettings() {
    val autostart =
        Intent().setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        )
    startFirstAvailable(autostart, appDetailsIntent())
}

/**
 * Системный диалог «Разрешить работу в фоне». Разрешение оправдано: плеер — основная функция приложения,
 * а без исключения HyperOS обрывает воспроизведение при выключенном экране.
 */
@SuppressLint("BatteryLife")
internal fun Context.requestIgnoreBatteryOptimizations() {
    val request =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.fromParts("package", packageName, null))
    startFirstAvailable(request, appDetailsIntent())
}

internal fun Context.appDetailsIntent(): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

private fun Context.startFirstAvailable(vararg intents: Intent) {
    for (intent in intents) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // Пробуем следующий вариант.
        } catch (_: SecurityException) {
            // Компонент есть, но не экспортирован на этой прошивке.
        }
    }
}

/** Разрешения для чтения музыки и уведомления плеера в зависимости от версии Android. */
internal fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO, android.Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }

internal val audioPermission: String
    get() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

/** Название папки из URI дерева SAF: «primary:Music/Rock» → «Rock». */
internal fun treeLabel(
    uri: Uri,
    fallback: String,
): String =
    DocumentsContract
        .getTreeDocumentId(uri)
        .substringAfter(':')
        .trimEnd('/')
        .substringAfterLast('/')
        .ifEmpty { fallback }
