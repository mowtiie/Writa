package com.mowtiie.writa.feature.settings

import com.mowtiie.writa.core.datastore.SettingsStore
import com.mowtiie.writa.core.datastore.Theme
import com.mowtiie.writa.core.datastore.testPreferences
import com.mowtiie.writa.core.network.HttpClients
import com.mowtiie.writa.core.network.apiJson
import com.mowtiie.writa.core.ui.theme.Contrast
import com.mowtiie.writa.feature.auth.AuthRepository
import com.mowtiie.writa.feature.notes.Note
import com.mowtiie.writa.feature.notes.NoteRepository
import com.mowtiie.writa.feature.notes.NotesFixture
import com.mowtiie.writa.feature.settings.backup.BackupCodec
import com.mowtiie.writa.feature.settings.backup.BackupNote
import com.mowtiie.writa.feature.settings.backup.BackupService
import com.mowtiie.writa.feature.settings.backup.DocumentOpener
import com.mowtiie.writa.feature.settings.backup.InvalidBackupException
import com.mowtiie.writa.feature.settings.backup.UnsupportedBackupVersionException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.QueueDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.Timeout
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val timeout: Timeout = Timeout.seconds(60)

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var fixture: NotesFixture
    private lateinit var repository: NoteRepository
    private lateinit var settingsStore: SettingsStore
    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val codec = FakeCodec()
    private val documents = FakeDocuments()

    private val server get() = fixture.server

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fixture = NotesFixture(folder.root.resolve("backend").also { it.mkdirs() }, clock, cacheDispatcher = dispatcher)
        fixture.start()
        (server.dispatcher as QueueDispatcher).setFailFast(MockResponse().setResponseCode(503))
        repository = NoteRepository(fixture.cache, fixture.backend.remote, fixture.engine, clock)
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        fixture.close()
        Dispatchers.resetMain()
    }

    // ---------------------------- helpers ----------------------------

    private fun TestScope.viewModel(): SettingsViewModel {
        val auth =
            AuthRepository(
                fixture.backend.connectionStore,
                fixture.backend.sessionStore,
                HttpClients.plain(),
                apiJson,
                Dispatchers.IO,
                fixture.scope,
            )
        val settings = SettingsStore(testPreferences(dataStoreScope, File(folder.root, "settings.preferences_pb")))
        settingsStore = settings
        val backup = BackupService(repository, fixture.backend.remote, codec, clock, dispatcher)
        val viewModel =
            SettingsViewModel(
                settings,
                repository,
                backup,
                documents,
                auth,
                fixture.backend.connectionStore,
                fixture.scope,
            )
        backgroundScope.launch(UnconfinedTestDispatcher(scheduler)) { viewModel.state.collect {} }
        return viewModel
    }

    private fun note(
        id: Long,
        archived: Boolean = false,
        deletedAt: String? = null,
    ) = Note(id, "t$id", "c$id", emptyList(), false, archived, "2026-11-01T10:00:00+00:00", null, deletedAt)

    private fun seed(note: Note) = runBlocking { fixture.dao.insert(fixture.codec.encode(note, note.id)) }

    private fun backupNote(n: Int) = BackupNote("t$n", "c$n", null, emptyList(), pinned = false, archived = false)

    private suspend fun SettingsViewModel.settled(): SettingsUiState {
        withContext(Dispatchers.Default) { withTimeout(WAIT_MS) { settingsStore.settings.first() } }
        scheduler.advanceUntilIdle()
        return state.value
    }

    // ---------------------------- appearance and privacy ----------------------------

    @Test
    fun theAppearanceAndPrivacyChoicesAreSaved() =
        runTest {
            val viewModel = viewModel()

            viewModel.onThemeChange(Theme.Dark)
            viewModel.awaiting { it.settings.theme == Theme.Dark }
            viewModel.onContrastChange(Contrast.High)
            viewModel.awaiting { it.settings.contrast == Contrast.High }
            viewModel.onDynamicColorChange(true)
            viewModel.awaiting { it.settings.dynamicColor }
            viewModel.onScreenPrivacyChange(true)
            val settings = viewModel.awaiting { it.settings.screenPrivacy }.settings

            assertEquals(Theme.Dark, settings.theme)
            assertEquals(Contrast.High, settings.contrast)
            assertTrue(settings.dynamicColor)
            assertTrue(settings.screenPrivacy)
        }

    @Test
    fun theNumberOfUnsentChangesIsKnownForTheServerWarning() =
        runTest {
            val viewModel = viewModel()
            assertEquals(0, viewModel.settled().pendingChanges)

            repository.create("offline", null, emptyList(), pinned = false)

            assertEquals(1, viewModel.settled().pendingChanges)
        }

    // ---------------------------- export ----------------------------

    @Test
    fun withoutNotesThereIsNothingToExport() =
        runTest {
            val viewModel = viewModel()

            viewModel.onExport()

            assertEquals(SettingsMessage.NothingToExport, viewModel.settled().message)
        }

    @Test
    fun exportAsksForAFileNamedAfterTheDate() =
        runTest {
            seed(note(1))
            val viewModel = viewModel()

            viewModel.onExport()
            scheduler.advanceUntilIdle()

            assertEquals(
                SettingsEvent.ChooseExportFile("writa_backup_20261105_120000.json"),
                viewModel.events.first(),
            )
        }

    @Test
    fun exportWritesTheNotesThatAreNotInTheTrash() =
        runTest {
            seed(note(1))
            seed(note(2, archived = true))
            seed(note(3, deletedAt = "2026-11-02T00:00:00Z"))
            val viewModel = viewModel()

            viewModel.onExportFileChosen("content://backup")
            val state = viewModel.settled()

            assertEquals(listOf("t1", "t2"), codec.written?.map { it.title }?.sorted())
            assertEquals(SettingsMessage.ExportDone, state.message)
            assertNull(state.busy)
        }

    @Test
    fun aFileThatCannotBeWrittenIsReportedAsAFailedExport() =
        runTest {
            seed(note(1))
            documents.failWith = IOException("disk full")
            val viewModel = viewModel()

            viewModel.onExportFileChosen("content://backup")
            val state = viewModel.settled()

            assertEquals(SettingsMessage.ExportFailed, state.message)
            assertNull(state.busy)
        }

    // ---------------------------- import ----------------------------

    @Test
    fun aChosenBackupIsShownForConfirmationBeforeAnythingIsSent() =
        runTest {
            codec.toRead = listOf(backupNote(1), backupNote(2))
            val viewModel = viewModel()

            viewModel.onImportFileChosen("content://backup")
            val state = viewModel.settled()

            assertEquals(2, state.importCount)
            assertNull(state.busy)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun confirmingAddsTheNotesAndRefreshes() =
        runTest {
            codec.toRead = listOf(backupNote(1), backupNote(2))
            val viewModel = viewModel()
            viewModel.onImportFileChosen("content://backup")
            scheduler.advanceUntilIdle()
            server.enqueue(MockResponse().setResponseCode(201))
            server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

            viewModel.onImportConfirm()
            val state = viewModel.settledWithRealIo()

            assertEquals(SettingsMessage.ImportDone(2), state.message)
            assertNull(state.importCount)
            assertNull(state.busy)
        }

    @Test
    fun decliningTheImportSendsNothing() =
        runTest {
            codec.toRead = listOf(backupNote(1))
            val viewModel = viewModel()
            viewModel.onImportFileChosen("content://backup")
            scheduler.advanceUntilIdle()

            viewModel.onImportDismiss()

            assertNull(viewModel.settled().importCount)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun aFileThatIsNotABackupIsRefused() =
        runTest {
            codec.readFailure = InvalidBackupException("no notes")
            val viewModel = viewModel()

            viewModel.onImportFileChosen("content://backup")

            assertEquals(SettingsMessage.ImportInvalid, viewModel.settled().message)
        }

    @Test
    fun aBackupWithoutUsableNotesIsRefused() =
        runTest {
            val viewModel = viewModel()

            viewModel.onImportFileChosen("content://backup")
            val state = viewModel.settled()

            assertEquals(SettingsMessage.ImportInvalid, state.message)
            assertNull(state.importCount)
        }

    @Test
    fun aBackupFromANewerVersionIsRefused() =
        runTest {
            codec.readFailure = UnsupportedBackupVersionException(2)
            val viewModel = viewModel()

            viewModel.onImportFileChosen("content://backup")

            assertEquals(SettingsMessage.ImportUnsupportedVersion, viewModel.settled().message)
        }

    @Test
    fun aFileThatCannotBeReadIsReportedAsAFailedImport() =
        runTest {
            documents.failWith = IOException("gone")
            val viewModel = viewModel()

            viewModel.onImportFileChosen("content://backup")
            val state = viewModel.settled()

            assertEquals(SettingsMessage.ImportFailed, state.message)
            assertNull(state.busy)
        }

    @Test
    fun anImportTheServerRefusesIsReportedAsFailed() =
        runTest {
            codec.toRead = listOf(backupNote(1))
            val viewModel = viewModel()
            viewModel.onImportFileChosen("content://backup")
            scheduler.advanceUntilIdle()

            viewModel.onImportConfirm()
            val state = viewModel.settledWithRealIo()

            assertEquals(SettingsMessage.ImportFailed, state.message)
        }

    @Test
    fun aMessageIsClearedOnceShown() =
        runTest {
            val viewModel = viewModel()
            viewModel.onExport()
            assertEquals(SettingsMessage.NothingToExport, viewModel.settled().message)

            viewModel.onMessageShown()

            assertNull(viewModel.settled().message)
        }

    // ---------------------------- change server ----------------------------

    @Test
    fun changingTheServerSignsOutWipesTheNotesAndForgetsTheConnection() =
        runTest {
            seed(note(1))
            val viewModel = viewModel()

            viewModel.onChangeServer()

            withContext(Dispatchers.Default) {
                withTimeout(WAIT_MS) { while (fixture.backend.connectionStore.current() != null) delay(POLL_MS) }
            }
            assertNull(fixture.backend.sessionStore.current())
            assertTrue(repository.observeActive().first().isEmpty())
            assertFalse(fixture.backend.connectionStore.current() != null)
        }

    /** The settings file is written on a real IO thread, so wait for the state to show the change. */
    private suspend fun SettingsViewModel.awaiting(condition: (SettingsUiState) -> Boolean): SettingsUiState {
        withContext(Dispatchers.Default) {
            withTimeout(WAIT_MS) {
                while (!condition(state.value)) {
                    scheduler.advanceUntilIdle()
                    delay(POLL_MS)
                }
            }
        }
        return state.value
    }

    /** Imports talk to the mock server on real IO threads, so wait for them instead of advancing virtual time. */
    private suspend fun SettingsViewModel.settledWithRealIo(): SettingsUiState {
        withContext(Dispatchers.Default) {
            withTimeout(WAIT_MS) {
                while (state.value.message == null) {
                    scheduler.advanceUntilIdle()
                    delay(POLL_MS)
                }
            }
        }
        return state.value
    }

    private class FakeCodec : BackupCodec {
        var written: List<BackupNote>? = null
        var toRead: List<BackupNote> = emptyList()
        var readFailure: IOException? = null

        override fun write(
            out: OutputStream,
            notes: List<BackupNote>,
            exportedAt: Instant,
        ) {
            written = notes
            out.write(1)
        }

        override fun read(input: InputStream): List<BackupNote> = readFailure?.let { throw it } ?: toRead
    }

    private class FakeDocuments : DocumentOpener {
        var failWith: IOException? = null

        override suspend fun write(
            uri: String,
            block: suspend (OutputStream) -> Unit,
        ) {
            failWith?.let { throw it }
            block(ByteArrayOutputStream())
        }

        override suspend fun <T> read(
            uri: String,
            block: suspend (InputStream) -> T,
        ): T {
            failWith?.let { throw it }
            return block(ByteArrayInputStream(ByteArray(0)))
        }
    }

    private companion object {
        const val WAIT_MS = 30_000L
        const val POLL_MS = 20L
    }
}
