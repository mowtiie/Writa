package com.mowtiie.writa.feature.trash

import com.mowtiie.writa.feature.notes.Note
import com.mowtiie.writa.feature.notes.NoteRepository
import com.mowtiie.writa.feature.notes.NotesFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.QueueDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.Timeout
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class TrashViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val timeout: Timeout = Timeout.seconds(20)

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var fixture: NotesFixture
    private lateinit var repository: NoteRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fixture = NotesFixture(folder.root.resolve("backend").also { it.mkdirs() }, clock, cacheDispatcher = dispatcher)
        fixture.start()
        (fixture.server.dispatcher as QueueDispatcher).setFailFast(MockResponse().setResponseCode(503))
        repository = NoteRepository(fixture.cache, fixture.backend.remote, fixture.engine, clock)
    }

    @After
    fun tearDown() {
        fixture.close()
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): TrashViewModel =
        TrashViewModel(
            repository,
        ).also { backgroundScope.launch(UnconfinedTestDispatcher(scheduler)) { it.state.collect {} } }

    private fun seed(
        id: Long,
        deletedAt: String? = "2026-11-02T00:00:00Z",
    ) = runBlocking {
        val note = Note(id, "t$id", "c$id", emptyList(), false, false, "2026-11-01T10:00:00+00:00", null, deletedAt)
        fixture.dao.insert(fixture.codec.encode(note, id))
    }

    private suspend fun TrashViewModel.loaded() = state.first { it.isLoaded }

    @Test
    fun theStateIsNotLoadedUntilTheNotesHaveBeenRead() =
        runTest {
            seed(1)
            val viewModel = viewModel()

            assertFalse(viewModel.state.value.isLoaded)
            assertEquals(listOf(1L), viewModel.loaded().notes.map { it.id })
        }

    @Test
    fun theMostRecentlyTrashedNoteIsFirst() =
        runTest {
            seed(1, deletedAt = "2026-11-02T00:00:00Z")
            seed(2, deletedAt = "2026-11-04T00:00:00Z")
            seed(3, deletedAt = "2026-11-03T00:00:00Z")
            seed(4, deletedAt = null)

            assertEquals(listOf(2L, 3L, 1L), viewModel().loaded().notes.map { it.id })
        }

    @Test
    fun restoringBringsTheNoteBackToTheMainList() =
        runTest {
            seed(1)
            val viewModel = viewModel()
            viewModel.loaded()

            viewModel.onRestore(1)
            advanceUntilIdle()

            assertTrue(
                viewModel.state.value.notes
                    .isEmpty(),
            )
            assertEquals(listOf(1L), repository.observeActive().first().map { it.id })
        }

    @Test
    fun deletingForeverRemovesOnlyThatNote() =
        runTest {
            seed(1)
            seed(2)
            val viewModel = viewModel()
            viewModel.loaded()

            viewModel.onDeleteForever(1)
            advanceUntilIdle()

            assertEquals(
                listOf(2L),
                viewModel.state.value.notes
                    .map { it.id },
            )
        }

    @Test
    fun emptyingTheTrashRemovesEveryNoteInIt() =
        runTest {
            seed(1)
            seed(2)
            seed(3, deletedAt = null)
            val viewModel = viewModel()
            viewModel.loaded()

            viewModel.onEmptyTrash()
            advanceUntilIdle()

            assertTrue(
                viewModel.state.value.notes
                    .isEmpty(),
            )
            assertEquals(listOf(3L), repository.observeActive().first().map { it.id })
        }

    @Test
    fun aChangeThatCannotBeMadeIsReportedUntilItIsShown() =
        runTest {
            val viewModel = viewModel()
            viewModel.loaded()

            viewModel.onRestore(99)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.actionFailed)

            viewModel.onMessageShown()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.actionFailed)
        }
}
