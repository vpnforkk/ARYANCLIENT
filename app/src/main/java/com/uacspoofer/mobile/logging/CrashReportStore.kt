package com.uacspoofer.mobile.logging

import android.app.Application
import android.content.Context
import android.os.Build
import com.uacspoofer.mobile.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReportStore {
    private const val FILE_NAME = "last_crash.txt"
    private const val MAX_CHARS = 24_000
    private const val LOG_TAIL = 80

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { persist(app, thread, error) }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    fun hasReport(context: Context): Boolean = file(context).isFile && file(context).length() > 0L

    fun read(context: Context): String? =
        runCatching { file(context).takeIf { it.isFile }?.readText(Charsets.UTF_8) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    fun copyPayload(context: Context): String? = read(context)

    internal fun format(
        thrown: Throwable,
        threadName: String,
        processName: String,
        versionName: String,
        versionCode: Int,
        logs: String,
        atMs: Long,
    ): String {
        val stack = StringWriter().also { writer ->
            thrown.printStackTrace(PrintWriter(writer))
        }.toString()
        val whenText = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(atMs))
        return buildString {
            appendLine("UAC crash report")
            appendLine("time=$whenText")
            appendLine("version=$versionName ($versionCode)")
            appendLine("sdk=${Build.VERSION.SDK_INT} ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("process=$processName")
            appendLine("thread=$threadName")
            appendLine()
            appendLine(stack.trim())
            if (logs.isNotBlank()) {
                appendLine()
                appendLine("--- recent logs ---")
                appendLine(logs)
            }
        }.take(MAX_CHARS)
    }

    private fun persist(app: Application, thread: Thread, error: Throwable) {
        val logs = runCatching {
            AppLogRepository.entries.value.takeLast(LOG_TAIL).joinToString("\n") { entry ->
                "${entry.timestamp} ${entry.level.label} ${entry.source.label} ${entry.message}"
            }
        }.getOrDefault("")
        val body = format(
            thrown = error,
            threadName = thread.name,
            processName = processName(app),
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            logs = logs,
            atMs = System.currentTimeMillis(),
        )
        val target = file(app)
        target.outputStream().use { out ->
            out.write(body.toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()
        }
    }

    private fun processName(app: Application): String =
        if (Build.VERSION.SDK_INT >= 28) {
            Application.getProcessName()
        } else {
            app.applicationInfo.processName.orEmpty()
        }.ifBlank { app.packageName }

    private fun file(context: Context): File = File(context.applicationContext.filesDir, FILE_NAME)
}
