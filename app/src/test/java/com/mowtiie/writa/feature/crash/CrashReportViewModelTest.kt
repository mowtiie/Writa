package com.mowtiie.writa.feature.crash

import com.mowtiie.writa.feature.settings.backup.DocumentOpener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class CrashReportViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC)
    private val documents = FakeDocuments()
    private lateinit var file: File
    private lateinit var store: CrashReportStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        file = File(folder.root, CrashReportStore.FILE_NAME)
        store = CrashReportStore(file)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = CrashReportViewModel(store, documents, clock, dispatcher)

    private fun withReport() = file.writeText("the report")

    // ---------------------------- whether there is a report ----------------------------

    @Test
    fun withoutAReportNothingIsOffered() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()

            assertFalse(viewModel.hasReport.value)
        }

    @Test
    fun aReportLeftByTheLastCrashIsOffered() =
        runTest {
            withReport()
            val viewModel = viewModel()
            advanceUntilIdle()

            assertTrue(viewModel.hasReport.value)
        }

    // ---------------------------- email ----------------------------

    @Test
    fun emailingSendsTheReportText() =
        runTest {
            withReport()
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onEmail()
            advanceUntilIdle()

            assertEquals(CrashEvent.SendEmail("the report"), viewModel.events.first())
            assertTrue(file.exists())
        }

    @Test
    fun onceTheEmailIsOpenedTheReportIsDeleted() =
        runTest {
            withReport()
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onEmailOpened()
            advanceUntilIdle()

            assertFalse(viewModel.hasReport.value)
            assertFalse(file.exists())
        }

    @Test
    fun withoutAnEmailAppTheReportIsKeptAndTheUserIsTold() =
        runTest {
            withReport()
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onNoEmailApp()
            advanceUntilIdle()

            assertEquals(CrashEvent.Show(CrashMessage.NoEmailApp), viewModel.events.first())
            assertTrue(viewModel.hasReport.value)
            assertTrue(file.exists())
        }

    @Test
    fun aReportThatVanishedBeforeEmailingIsJustDroppedFromTheOffer() =
        runTest {
            withReport()
            val viewModel = viewModel()
            advanceUntilIdle()
            file.delete()

            viewModel.onEmail()
            advanceUntilIdle()

            assertFalse(viewModel.hasReport.value)
        }

    // ---------------------------- save to file ----------------------------

    @Test
    fun savingAsksForAFileNamedAfterTheTime() =
        runTest {
            withReport()
            val viewModel = viewModel()

            viewModel.onSave()

            assertEquals(CrashEvent.ChooseSaveFile("writa_crash_20261105_120000.txt"), viewModel.events.first())
        }

    @Test
    fun theReportIsWrittenToTheChosenFileAndThenDeleted() =
        runTest {
            withReport()
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onSaveFileChosen("content://crash")
            advanceUntilIdle()

            assertEquals("the report", documents.written.toString(Charsets.UTF_8))
            assertEquals(CrashEvent.Show(CrashMessage.Saved), viewModel.events.first())
            assertFalse(viewModel.hasReport.value)
            assertFalse(file.exists())
        }

    @Test
    fun aFileThatCannotBeWrittenKeepsTheReportAndTellsTheUser() =
        runTest {
            withReport()
            documents.failWith = IOException("disk full")
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onSaveFileChosen("content://crash")
            advanceUntilIdle()

            assertEquals(CrashEvent.Show(CrashMessage.SaveFailed), viewModel.events.first())
            assertTrue(viewModel.hasReport.value)
            assertTrue(file.exists())
        }

    @Test
    fun aReportThatVanishedBeforeSavingIsAFailedSave() =
        runTest {
            withReport()
            val viewModel = viewModel()
            advanceUntilIdle()
            file.delete()

            viewModel.onSaveFileChosen("content://crash")
            advanceUntilIdle()

            assertEquals(CrashEvent.Show(CrashMessage.SaveFailed), viewModel.events.first())
        }

    // ---------------------------- dismiss ----------------------------

    @Test
    fun dismissingDeletesTheReport() =
        runTest {
            withReport()
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onDismiss()
            advanceUntilIdle()

            assertFalse(viewModel.hasReport.value)
            assertFalse(file.exists())
        }

    private class FakeDocuments : DocumentOpener {
        val written = ByteArrayOutputStream()
        var failWith: IOException? = null

        override suspend fun write(
            uri: String,
            block: suspend (OutputStream) -> Unit,
        ) {
            failWith?.let { throw it }
            block(written)
        }

        override suspend fun <T> read(
            uri: String,
            block: suspend (InputStream) -> T,
        ): T = error("not used")
    }
}
