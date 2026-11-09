package dev.fuzz.core.player.widget

import android.appwidget.AppWidgetManager
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.fuzz.core.player.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PlayerWidgetTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val shadow = shadowOf(AppWidgetManager.getInstance(context))
    private val id = shadow.createWidget(PlayerWidgetProvider::class.java, R.layout.widget_player)

    private fun text(view: Int) = shadow.getViewFor(id).findViewById<TextView>(view).text.toString()

    @Test
    fun `shows the playing track and falls back to a hint`() {
        PlayerWidget.render(context, WidgetState("Кукушка", "Кино", playing = true))
        assertEquals("Кукушка", text(R.id.widget_title))
        assertEquals("Кино", text(R.id.widget_artist))

        PlayerWidget.render(context, WidgetState())
        assertEquals("Fuzz", text(R.id.widget_title))
    }
}
