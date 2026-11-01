package com.mowtiie.writa.feature.crash

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** The report of the last crash, kept in one file until the user has dealt with it on the next start. */
class CrashReportStore(
    private val file: File,
) {
    fun hasReport(): Boolean = file.exists()

    /** The report, or null if there is none or it cannot be read. */
    fun read(): String? =
        try {
            if (file.exists()) file.readText(Charsets.UTF_8) else null
        } catch (_: IOException) {
            null
        }

    /** Replaces any earlier report. Throws [IOException] if the file cannot be written. */
    fun write(report: String) {
        file.parentFile?.mkdirs()
        file.writeText(report, Charsets.UTF_8)
    }

    fun delete() {
        file.delete()
    }

    /** Writes the report to [out], for "save to file". False if there is none or it cannot be written. */
    fun copyTo(out: OutputStream): Boolean {
        val report = read() ?: return false
        return try {
            out.write(report.toByteArray(Charsets.UTF_8))
            true
        } catch (_: IOException) {
            false
        }
    }

    companion object {
        const val FILE_NAME = "last_crash.txt"
        private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC)

        fun forApp(context: Context) = CrashReportStore(File(context.filesDir, FILE_NAME))

        fun suggestedFileName(at: Instant): String = "writa_crash_${FILE_STAMP.format(at)}.txt"
    }
}
