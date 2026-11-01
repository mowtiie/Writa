package com.mowtiie.writa.feature.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CrashHandlerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file get() = folder.root.resolve("files/last_crash.txt")
    private val store get() = CrashReportStore(file)

    private val info =
        CrashInfo("t", "worker-1", "com.mowtiie.writa", "1.0.0", 1, "Google", "Pixel", "16", 36)
    private val provider = CrashInfoProvider { thread -> info.copy(threadName = thread.name) }

    private class RecordingHandler(
        private val onCrash: () -> Unit = {},
    ) : Thread.UncaughtExceptionHandler {
        var thread: Thread? = null
        var error: Throwable? = null
        var calls = 0

        override fun uncaughtException(
            thread: Thread,
            error: Throwable,
        ) {
            onCrash()
            calls++
            this.thread = thread
            this.error = error
        }
    }

    @Test
    fun aCrashIsWrittenToTheReportFile() {
        val handler = CrashHandler(store, provider, previous = RecordingHandler())

        handler.uncaughtException(Thread("worker-1"), IllegalStateException("never in the report"))

        val text = file.readText()
        assertTrue(text.contains("Thread: worker-1"))
        assertTrue(text.contains("java.lang.IllegalStateException"))
        assertFalse(text.contains("never in the report"))
    }

    @Test
    fun theCrashIsPassedOnAfterTheReportIsWritten() {
        var reportExistedWhenPassedOn = false
        val previous = RecordingHandler { reportExistedWhenPassedOn = file.exists() }
        val thread = Thread("worker-1")
        val error = RuntimeException()

        CrashHandler(store, provider, previous).uncaughtException(thread, error)

        assertEquals(1, previous.calls)
        assertSame(thread, previous.thread)
        assertSame(error, previous.error)
        assertTrue(reportExistedWhenPassedOn)
    }

    @Test
    fun aReportThatCannotBeWrittenStillPassesTheCrashOn() {
        file.mkdirs() // a directory where the file should be, so writing fails
        val previous = RecordingHandler()

        CrashHandler(store, provider, previous).uncaughtException(Thread(), RuntimeException())

        assertEquals(1, previous.calls)
    }

    @Test
    fun aFailingInfoProviderStillPassesTheCrashOn() {
        val previous = RecordingHandler()
        val failing = CrashInfoProvider { throw IllegalStateException("no package manager") }

        CrashHandler(store, failing, previous).uncaughtException(Thread(), RuntimeException())

        assertEquals(1, previous.calls)
        assertFalse(file.exists())
    }

    @Test
    fun aFailureWhileWritingEvenAnErrorDoesNotEscape() {
        val previous = RecordingHandler()
        val failing = CrashInfoProvider { throw StackOverflowError() }

        CrashHandler(store, failing, previous).uncaughtException(Thread(), RuntimeException())

        assertEquals(1, previous.calls)
    }

    @Test
    fun withoutAPreviousHandlerTheProcessIsTerminated() {
        var terminated = 0

        CrashHandler(store, provider, previous = null, terminate = { terminated++ })
            .uncaughtException(Thread(), RuntimeException())

        assertEquals(1, terminated)
        assertTrue(file.exists())
    }

    @Test
    fun withAPreviousHandlerTheProcessIsNotTerminatedByUs() {
        var terminated = 0

        CrashHandler(store, provider, RecordingHandler(), terminate = { terminated++ })
            .uncaughtException(Thread(), RuntimeException())

        assertEquals(0, terminated)
    }

    @Test
    fun aLaterCrashReplacesAnEarlierReport() {
        val handler = CrashHandler(store, provider, RecordingHandler())

        handler.uncaughtException(Thread("first"), RuntimeException())
        handler.uncaughtException(Thread("second"), RuntimeException())

        val text = file.readText()
        assertTrue(text.contains("Thread: second"))
        assertFalse(text.contains("Thread: first"))
    }

    @Test
    fun aThreadThatReallyDiesLeavesAReport() {
        val previous = RecordingHandler()
        val thread = Thread { throw UnsupportedOperationException("boom") }
        thread.name = "dying-thread"
        thread.uncaughtExceptionHandler = CrashHandler(store, provider, previous)

        thread.start()
        thread.join(JOIN_MS)

        assertEquals(1, previous.calls)
        val text = file.readText()
        assertTrue(text.contains("Thread: dying-thread"))
        assertTrue(text.contains("java.lang.UnsupportedOperationException"))
        assertNotNull(previous.error)
    }

    private companion object {
        const val JOIN_MS = 5_000L
    }
}
