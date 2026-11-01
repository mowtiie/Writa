package com.mowtiie.writa.feature.crash

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.Instant

class CrashReportStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file get() = folder.root.resolve("files/last_crash.txt")
    private val store get() = CrashReportStore(file)

    @Test
    fun withoutAReportThereIsNothingToRead() {
        assertFalse(store.hasReport())
        assertNull(store.read())
    }

    @Test
    fun aWrittenReportIsReadBackExactly() {
        store.write("line one\nline é✓\n")

        assertTrue(store.hasReport())
        assertEquals("line one\nline é✓\n", store.read())
    }

    @Test
    fun theFolderIsCreatedWhenMissing() {
        assertFalse(file.parentFile!!.exists())

        store.write("x")

        assertTrue(file.exists())
    }

    @Test
    fun aNewReportReplacesTheOldOne() {
        store.write("first, long report")
        store.write("second")

        assertEquals("second", store.read())
    }

    @Test
    fun deletingForgetsTheReport() {
        store.write("x")

        store.delete()

        assertFalse(store.hasReport())
        assertNull(store.read())
    }

    @Test
    fun deletingWithoutAReportIsFine() {
        store.delete()

        assertFalse(store.hasReport())
    }

    @Test
    fun copyToWritesTheReportBytes() {
        store.write("report é")
        val out = ByteArrayOutputStream()

        assertTrue(store.copyTo(out))

        assertArrayEquals("report é".toByteArray(Charsets.UTF_8), out.toByteArray())
    }

    @Test
    fun copyToWithoutAReportWritesNothing() {
        val out = ByteArrayOutputStream()

        assertFalse(store.copyTo(out))

        assertEquals(0, out.size())
    }

    @Test
    fun copyToReportsAFailingStream() {
        store.write("x")
        val broken =
            object : OutputStream() {
                override fun write(b: Int) = throw IOException("disk full")
            }

        assertFalse(store.copyTo(broken))
    }

    @Test
    fun aReportThatCannotBeReadIsNull() {
        file.mkdirs() // a directory where the file should be

        assertNull(store.read())
    }

    @Test
    fun theSuggestedFileNameCarriesTheUtcTime() {
        assertEquals(
            "writa_crash_20261105_123009.txt",
            CrashReportStore.suggestedFileName(Instant.parse("2026-11-05T12:30:09Z")),
        )
    }

    @Test
    fun theFileNameIsStable() {
        assertEquals("last_crash.txt", CrashReportStore.FILE_NAME)
    }
}
