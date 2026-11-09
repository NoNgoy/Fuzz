package dev.fuzz.feature.library

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import dev.fuzz.core.model.Track
import dev.fuzz.core.model.TrackId

/** Файл трека лежит в общей медиатеке телефона, и его можно удалить через системный запрос. */
internal fun Track.isDeviceFile(): Boolean = playbackUri.startsWith("content://media/")

/**
 * Удаление файла с телефона. Android сам спрашивает подтверждение у человека, приложение
 * лишь просит: на Android 11+ это createDeleteRequest, на Android 10 — RecoverableSecurityException.
 * Возвращает функцию, которую зовут из меню трека.
 */
@Composable
internal fun rememberDeleteFromDevice(
    onDeleted: (TrackId) -> Unit,
    onFailed: () -> Unit,
): (Track) -> Unit {
    val resolver = LocalContext.current.contentResolver
    val deleted by rememberUpdatedState(onDeleted)
    val failed by rememberUpdatedState(onFailed)
    var pending by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val (id, uri) = pending ?: return@rememberLauncherForActivityResult
            pending = null
            if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
            // На Android 11+ система уже удалила файл; на Android 10 разрешение получено, удаляем сами.
            val done =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ||
                    runCatching { resolver.tryDelete(uri.toUri()) == null }.getOrDefault(false)
            if (done) deleted(TrackId(id)) else failed()
        }
    return remember(resolver, launcher) {
        { track ->
            val uri = track.playbackUri.toUri()
            val sender: Result<IntentSender?> =
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        MediaStore.createDeleteRequest(resolver, listOf(uri)).intentSender
                    } else {
                        resolver.tryDelete(uri)
                    }
                }
            sender
                .onSuccess { request ->
                    if (request == null) {
                        deleted(track.id)
                    } else {
                        pending = track.id.value to track.playbackUri
                        launcher.launch(IntentSenderRequest.Builder(request).build())
                    }
                }.onFailure { failed() }
        }
    }
}

/** null, если файл удалён; иначе запрос разрешения, который нужно показать человеку. */
private fun ContentResolver.tryDelete(uri: Uri): IntentSender? =
    try {
        delete(uri, null, null)
        null
    } catch (e: RecoverableSecurityException) {
        e.userAction.actionIntent.intentSender
    }
