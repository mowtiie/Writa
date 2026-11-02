package com.mowtiie.writa.feature.crash

import android.content.Context
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.mowtiie.writa.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CrashHandlerAndroidTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(context.cacheDir, "crash_test_report.txt")

    @Before
    fun startClean() {
        file.delete()
    }

    @After
    fun cleanUp() {
        file.delete()
    }

    @Test
    fun theProviderReportsThisAppAndDevice() {
        val clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC)

        val info = AndroidCrashInfoProvider(context, clock).infoFor(Thread.currentThread())

        assertEquals("2026-11-05T12:00:00Z", info.time)
        assertEquals(Thread.currentThread().name, info.threadName)
        assertEquals(context.packageName, info.packageName)
        assertEquals(BuildConfig.VERSION_NAME, info.versionName)
        assertEquals(1L, info.versionCode)
        assertEquals(Build.MODEL, info.model)
        assertEquals(Build.VERSION.SDK_INT, info.sdkInt)
    }

    @Test
    fun aCrashIsWrittenWithTheRealDeviceFactsAndNoMessage() {
        var passedOn = false
        val handler =
            CrashHandler(
                store = CrashReportStore(file),
                infoProvider = AndroidCrashInfoProvider(context),
                previous = { _, _ -> passedOn = true },
            )

        handler.uncaughtException(Thread.currentThread(), IllegalStateException("secret note text"))

        val text = file.readText()
        assertTrue(passedOn)
        assertTrue(text.contains("Package: ${context.packageName}"))
        assertTrue(text.contains("Version: ${BuildConfig.VERSION_NAME} (1)"))
        assertTrue(text.contains("API ${Build.VERSION.SDK_INT}"))
        assertTrue(text.contains("java.lang.IllegalStateException"))
        assertFalse(text.contains("secret note text"))
    }

    @Test
    fun theAppStoreLivesInTheFilesFolderUnderTheAgreedName() {
        val store = CrashReportStore.forApp(context)
        store.delete()

        store.write("x")

        assertTrue(File(context.filesDir, "last_crash.txt").exists())
        store.delete()
    }
}
