package com.mowtiie.writa.feature.crash

/** The facts about the app and device that go into a crash report. */
data class CrashInfo(
    val time: String,
    val threadName: String,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long?,
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
)

/**
 * Builds the text of a crash report. Exception messages are left out on purpose: the report is emailed or saved
 * by the user, and a message can quote private data (a parse error quotes part of the JSON it choked on, which
 * could be note text or a token). The class names, stack frames and cause chain are enough to find the bug.
 */
object CrashReportFormatter {
    const val MAX_FRAMES_PER_EXCEPTION = 60
    private const val MAX_CAUSES = 10

    fun format(
        info: CrashInfo,
        error: Throwable,
    ): String =
        buildString {
            appendLine("=== Writa Crash Report ===")
            appendLine("Time: ${info.time}")
            appendLine("Thread: ${info.threadName}")
            appendLine()
            appendLine("--- App Info ---")
            appendLine("Package: ${info.packageName}")
            appendLine("Version: ${info.versionName ?: "unknown"} (${info.versionCode ?: "unknown"})")
            appendLine()
            appendLine("--- Device Info ---")
            appendLine("Manufacturer: ${info.manufacturer}")
            appendLine("Model: ${info.model}")
            appendLine("Android: ${info.androidRelease} (API ${info.sdkInt})")
            appendLine()
            appendLine("--- Stack Trace ---")
            appendStackTrace(error)
        }

    private fun StringBuilder.appendStackTrace(error: Throwable) {
        val seen = mutableSetOf<Throwable>()
        var current: Throwable? = error
        var depth = 0
        while (current != null && seen.add(current) && depth < MAX_CAUSES) {
            appendLine(if (depth == 0) current.javaClass.name else "Caused by: ${current.javaClass.name}")
            val frames = current.stackTrace
            frames.take(MAX_FRAMES_PER_EXCEPTION).forEach { appendLine("\tat $it") }
            if (frames.size > MAX_FRAMES_PER_EXCEPTION) {
                appendLine("\t... ${frames.size - MAX_FRAMES_PER_EXCEPTION} more")
            }
            current = current.cause
            depth++
        }
    }
}
