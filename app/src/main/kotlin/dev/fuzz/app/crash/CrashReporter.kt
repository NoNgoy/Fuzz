package dev.fuzz.app.crash

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import dev.fuzz.app.BuildConfig
import kotlin.system.exitProcess

/**
 * Ловит падение приложения и показывает текст ошибки на отдельном экране, который живёт
 * в своём процессе (`:crash`). Так тестировщик без компьютера и adb может скопировать ошибку
 * и прислать её, а не просто видеть, что приложение «не открывается».
 */
object CrashReporter {
    private const val CRASH_PROCESS_SUFFIX = ":crash"
    private const val EXIT_CODE = 10

    /** Процесс экрана ошибки: в нём не запускаем фоновую работу приложения. */
    fun isCrashProcess(): Boolean = Application.getProcessName().endsWith(CRASH_PROCESS_SUFFIX)

    fun install(context: Context) {
        if (isCrashProcess()) return
        val app = context.applicationContext ?: context
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val shown =
                runCatching {
                    app.startActivity(
                        Intent(app, CrashActivity::class.java)
                            .putExtra(CrashActivity.EXTRA_REPORT, report(thread, error))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                    )
                }.isSuccess
            if (shown) {
                Process.killProcess(Process.myPid())
                exitProcess(EXIT_CODE)
            } else {
                previous?.uncaughtException(thread, error)
            }
        }
    }

    internal fun report(
        thread: Thread,
        error: Throwable,
    ): String =
        buildString {
            appendLine("Fuzz ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Поток: ${thread.name}")
            appendLine()
            append(error.stackTraceToString())
        }
}
