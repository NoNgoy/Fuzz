package dev.fuzz.app.crash

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import dev.fuzz.app.MainActivity
import dev.fuzz.app.R

/**
 * Экран «Fuzz упал». Нарочно без Compose, Hilt и тем приложения: он должен открыться,
 * даже если сломалось именно это.
 */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = intent.getStringExtra(EXTRA_REPORT).orEmpty()
        val padding = (PADDING_DP * resources.displayMetrics.density).toInt()

        val title =
            TextView(this).apply {
                setText(R.string.crash_title)
                textSize = TITLE_SP
                setTypeface(typeface, Typeface.BOLD)
            }
        val hint = TextView(this).apply { setText(R.string.crash_hint) }
        val copy =
            Button(this).apply {
                setText(R.string.crash_copy)
                setOnClickListener { copy(report) }
            }
        val share =
            Button(this).apply {
                setText(R.string.crash_share)
                setOnClickListener { share(report) }
            }
        val restart =
            Button(this).apply {
                setText(R.string.crash_restart)
                setOnClickListener {
                    startActivity(Intent(this@CrashActivity, MainActivity::class.java))
                    finish()
                }
            }
        val details =
            TextView(this).apply {
                text = report
                typeface = Typeface.MONOSPACE
                textSize = DETAILS_SP
                setTextIsSelectable(true)
            }
        val column =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding * 2, padding, padding)
                listOf(title, hint, copy, share, restart, details).forEach { addView(it, MATCH_PARENT, WRAP_CONTENT) }
            }
        setContentView(ScrollView(this).apply { addView(column, MATCH_PARENT, WRAP_CONTENT) })
    }

    private fun copy(report: String) {
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(getString(R.string.crash_title), report))
        Toast.makeText(this, R.string.crash_copied, Toast.LENGTH_SHORT).show()
    }

    private fun share(report: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
        startActivity(Intent.createChooser(send, getString(R.string.crash_share)))
    }

    companion object {
        const val EXTRA_REPORT = "dev.fuzz.crash.REPORT"
        private const val PADDING_DP = 16
        private const val TITLE_SP = 22f
        private const val DETAILS_SP = 11f
    }
}
