package dev.fuzz.core.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.KeyEvent
import android.widget.RemoteViews
import dev.fuzz.core.player.R
import dev.fuzz.core.player.service.FuzzPlaybackService

/** Что показывает виджет. Пустой [title] — ничего не играло: подсказка «нажмите ▶». */
internal data class WidgetState(
    val title: String = "",
    val artist: String = "",
    val playing: Boolean = false,
    val artwork: Bitmap? = null,
)

/**
 * Виджет «сейчас играет». Сам ничего не хранит: сервис плеера перерисовывает его при каждой смене трека
 * или паузы, а без сервиса виджет показывает подсказку. Кнопки шлют медиакнопки в сервис, так же как
 * уведомление Media3, поэтому ▶ после перезагрузки продолжает с того места, где остановились.
 */
class PlayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        PlayerWidget.render(context, WidgetState(), manager, ids)
    }
}

internal object PlayerWidget {
    fun render(
        context: Context,
        state: WidgetState,
        manager: AppWidgetManager = AppWidgetManager.getInstance(context),
        ids: IntArray = manager.getAppWidgetIds(ComponentName(context, PlayerWidgetProvider::class.java)),
    ) {
        if (ids.isEmpty()) return
        manager.updateAppWidget(ids, views(context, state))
    }

    private fun views(
        context: Context,
        state: WidgetState,
    ) = RemoteViews(context.packageName, R.layout.widget_player).apply {
        val idle = state.title.isEmpty()
        setTextViewText(
            R.id.widget_title,
            if (idle) context.getString(R.string.player_widget_idle_title) else state.title,
        )
        setTextViewText(
            R.id.widget_artist,
            if (idle) context.getString(R.string.player_widget_idle_subtitle) else state.artist,
        )
        if (state.artwork != null) {
            setImageViewBitmap(R.id.widget_artwork, state.artwork)
        } else {
            setImageViewResource(R.id.widget_artwork, R.drawable.ic_widget_artwork)
        }
        setImageViewResource(
            R.id.widget_play,
            if (state.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )
        setContentDescription(
            R.id.widget_play,
            context.getString(if (state.playing) R.string.player_widget_pause else R.string.player_widget_play),
        )
        // ▶ может разбудить сервис, который ещё не запущен: так делает и уведомление Media3.
        setOnClickPendingIntent(
            R.id.widget_play,
            mediaButton(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, foreground = !state.playing),
        )
        setOnClickPendingIntent(R.id.widget_next, mediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT))
        setOnClickPendingIntent(R.id.widget_previous, mediaButton(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        openApp(context)?.let { setOnClickPendingIntent(R.id.widget_root, it) }
    }

    private fun mediaButton(
        context: Context,
        keyCode: Int,
        foreground: Boolean = false,
    ): PendingIntent {
        val intent =
            Intent(Intent.ACTION_MEDIA_BUTTON)
                .setComponent(ComponentName(context, FuzzPlaybackService::class.java))
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return if (foreground) {
            PendingIntent.getForegroundService(context, keyCode, intent, flags)
        } else {
            PendingIntent.getService(context, keyCode, intent, flags)
        }
    }

    private fun openApp(context: Context): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
}
