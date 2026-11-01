package com.mowtiie.writa.feature.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportFormatterTest {
    private val info =
        CrashInfo(
            time = "2026-11-05T12:00:00Z",
            threadName = "main",
            packageName = "com.mowtiie.writa",
            versionName = "1.0.0",
            versionCode = 1,
            manufacturer = "Google",
            model = "Pixel 9",
            androidRelease = "16",
            sdkInt = 36,
        )

    private fun report(error: Throwable) = CrashReportFormatter.format(info, error)

    @Test
    fun theReportCarriesTheAppAndDeviceFacts() {
        val text = report(IllegalStateException())

        assertTrue(text.startsWith("=== Writa Crash Report ===\n"))
        assertTrue(text.contains("Time: 2026-11-05T12:00:00Z\n"))
        assertTrue(text.contains("Thread: main\n"))
        assertTrue(text.contains("Package: com.mowtiie.writa\n"))
        assertTrue(text.contains("Version: 1.0.0 (1)\n"))
        assertTrue(text.contains("Manufacturer: Google\n"))
        assertTrue(text.contains("Model: Pixel 9\n"))
        assertTrue(text.contains("Android: 16 (API 36)\n"))
        assertTrue(text.contains("--- Stack Trace ---\n"))
    }

    @Test
    fun anUnknownVersionIsSaidSo() {
        val text = CrashReportFormatter.format(info.copy(versionName = null, versionCode = null), RuntimeException())

        assertTrue(text.contains("Version: unknown (unknown)\n"))
    }

    @Test
    fun theStackTraceNamesTheExceptionClassAndItsFrames() {
        val text = report(IllegalStateException("x"))

        assertTrue(text.contains("java.lang.IllegalStateException\n"))
        assertTrue(text.contains("\tat com.mowtiie.writa.feature.crash.CrashReportFormatterTest."))
    }

    @Test
    fun exceptionMessagesNeverReachTheReport() {
        val secret = "Bearer eyJhbGciOi.SECRET-TOKEN my private note text"
        val error = IllegalStateException(secret, RuntimeException("JSON input: {\"title\":\"$secret\"}"))

        val text = report(error)

        assertFalse(text.contains("SECRET-TOKEN"))
        assertFalse(text.contains("private note"))
        assertFalse(text.contains("JSON input"))
    }

    @Test
    fun theCauseChainIsListedInOrder() {
        val error = IllegalStateException("outer", java.io.IOException("middle", IllegalArgumentException("inner")))

        val lines = report(error).lines()

        val classes =
            lines.filter {
                !it.startsWith("\t") && ("Exception" in it) &&
                    ("Caused by" in it || it.startsWith("java."))
            }
        assertEquals(
            listOf(
                "java.lang.IllegalStateException",
                "Caused by: java.io.IOException",
                "Caused by: java.lang.IllegalArgumentException",
            ),
            classes,
        )
    }

    @Test
    fun aCauseThatPointsBackAtTheExceptionDoesNotLoop() {
        val first = RuntimeException("a")
        val second = RuntimeException("b", first)
        first.initCause(second)

        val text = report(first)

        assertEquals(2, text.lines().count { it.contains("java.lang.RuntimeException") })
    }

    @Test
    fun aLongCauseChainIsCutOff() {
        var error: Throwable = RuntimeException("0")
        repeat(40) { error = RuntimeException("$it", error) }

        val count = report(error).lines().count { it.contains("java.lang.RuntimeException") }

        assertEquals(10, count)
    }

    @Test
    fun aHugeStackIsCutToTheFirstFramesWithACount() {
        val error = RuntimeException()
        error.stackTrace = Array(500) { StackTraceElement("Cls", "method$it", "File.kt", it) }

        val text = report(error)

        assertEquals(CrashReportFormatter.MAX_FRAMES_PER_EXCEPTION, text.lines().count { it.startsWith("\tat Cls.") })
        assertTrue(text.contains("\t... 440 more\n"))
        assertTrue(text.contains("\tat Cls.method0(File.kt:0)\n"))
        assertFalse(text.contains("method60("))
    }

    @Test
    fun anExceptionWithoutFramesIsStillReported() {
        val error = RuntimeException()
        error.stackTrace = emptyArray()

        assertTrue(report(error).trimEnd().endsWith("java.lang.RuntimeException"))
    }
}
