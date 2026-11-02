package com.mowtiie.writa.feature.editor

import androidx.lifecycle.SavedStateHandle
import com.mowtiie.writa.feature.notes.Note
import com.mowtiie.writa.feature.notes.NoteRepository
import com.mowtiie.writa.feature.notes.NotesFixture
import com.mowtiie.writa.navigation.EditorRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
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

    // ---------------------------- helpers ----------------------------

    private fun route(noteId: Long) = SavedStateHandle(mapOf(EditorRoute.NOTE_ID_KEY to noteId))

    private fun newNote(saved: SavedStateHandle = route(EditorRoute.NEW_NOTE)) = EditorViewModel(saved, repository)

    private fun note(
        id: Long,
        title: String = "t$id",
        content: String? = "c$id",
        tags: List<String> = emptyList(),
        pinned: Boolean = false,
    ) = Note(id, title, content, tags, pinned, false, "2026-11-01T10:00:00+00:00", "2026-11-01T10:00:00+00:00", null)

    private fun seed(note: Note) = runBlocking { fixture.dao.insert(fixture.codec.encode(note, note.id)) }

    private suspend fun open(
        id: Long,
        saved: SavedStateHandle = route(id),
    ): EditorViewModel = EditorViewModel(saved, repository).also { scheduler.advanceUntilIdle() }

    private suspend fun EditorViewModel.closed() = events.first() == EditorEvent.Close

    // ---------------------------- a new note ----------------------------

    @Test
    fun aNewNoteStartsEmptyAndReady() =
        runTest {
            val viewModel = newNote()

            assertEquals(EditorLoad.Ready, viewModel.state.value.load)
            assertTrue(viewModel.state.value.isNew)
            assertFalse(viewModel.state.value.hasUnsavedChanges)
        }

    @Test
    fun savingANewNoteCreatesItAndClosesTheScreen() =
        runTest {
            val viewModel = newNote()
            viewModel.onTitleChange("  Groceries ")
            viewModel.onContentChange(" milk \n")
            viewModel.onTagInputChange("Home, #shop")
            viewModel.onPinToggle()

            viewModel.onSave()
            advanceUntilIdle()

            assertTrue(viewModel.closed())
            val saved = repository.observeActive().first().single()
            assertEquals("Groceries", saved.title)
            assertEquals("milk", saved.content)
            assertEquals(listOf("Home", "shop"), saved.tags)
            assertTrue(saved.pinned)
        }

    @Test
    fun aBlankContentIsSavedAsNoContent() =
        runTest {
            val viewModel = newNote()
            viewModel.onTitleChange("Title")
            viewModel.onContentChange("   ")

            viewModel.onSave()
            advanceUntilIdle()

            assertNull(
                repository
                    .observeActive()
                    .first()
                    .single()
                    .content,
            )
        }

    @Test
    fun aMissingTitleIsReportedOnTheFieldAndNothingIsSaved() =
        runTest {
            val viewModel = newNote()
            viewModel.onTitleChange("   ")

            viewModel.onSave()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.titleMissing)
            assertFalse(viewModel.state.value.isSaving)
            assertTrue(repository.observeActive().first().isEmpty())

            viewModel.onTitleChange("x")
            assertFalse(viewModel.state.value.titleMissing)
        }

    @Test
    fun trashingANewNoteJustClosesTheScreen() =
        runTest {
            val viewModel = newNote()
            viewModel.onTitleChange("never saved")

            viewModel.onTrash()

            assertTrue(viewModel.closed())
            assertTrue(repository.observeActive().first().isEmpty())
        }

    // ---------------------------- an existing note ----------------------------

    @Test
    fun anExistingNoteIsReadFromTheDatabase() =
        runTest {
            seed(note(7, "Seven", "body", listOf("a"), pinned = true))

            val state = open(7).state.value

            assertEquals(EditorLoad.Ready, state.load)
            assertFalse(state.isNew)
            assertEquals(NoteDraft("Seven", "body", listOf("a"), true), state.draft)
            assertFalse(state.hasUnsavedChanges)
        }

    @Test
    fun aNoteThatIsGoneIsUnavailable() =
        runTest {
            assertEquals(EditorLoad.Unavailable, open(99).state.value.load)
        }

    @Test
    fun editsAreUnsavedUntilTheyAreChangedBack() =
        runTest {
            seed(note(7, "Seven"))
            val viewModel = open(7)

            viewModel.onTitleChange("Seven!")
            assertTrue(viewModel.state.value.hasUnsavedChanges)

            viewModel.onTitleChange(" Seven ")
            assertFalse(viewModel.state.value.hasUnsavedChanges)
        }

    @Test
    fun textStillInTheTagFieldCountsAsAChange() =
        runTest {
            seed(note(7))
            val viewModel = open(7)

            viewModel.onTagInputChange("new")

            assertTrue(viewModel.state.value.hasUnsavedChanges)
        }

    @Test
    fun savingAnExistingNoteStoresTheEdits() =
        runTest {
            seed(note(7, "Seven", "old"))
            val viewModel = open(7)
            viewModel.onTitleChange("Seven v2")
            viewModel.onContentChange("new")
            viewModel.onTagInputChange("x")

            viewModel.onSave()
            advanceUntilIdle()

            assertTrue(viewModel.closed())
            val saved = repository.get(7)
            assertEquals("Seven v2", saved?.title)
            assertEquals("new", saved?.content)
            assertEquals(listOf("x"), saved?.tags)
        }

    @Test
    fun archivingSavesTheEditsAndMovesTheNoteToTheArchive() =
        runTest {
            seed(note(7, "Seven"))
            val viewModel = open(7)
            viewModel.onTitleChange("Seven v2")

            viewModel.onArchiveToggle()
            advanceUntilIdle()

            assertTrue(viewModel.closed())
            assertTrue(repository.observeActive().first().isEmpty())
            assertEquals(listOf("Seven v2"), repository.observeArchived().first().map { it.title })
        }

    @Test
    fun anArchivedNoteOffersToUnarchiveItself() =
        runTest {
            seed(note(7).copy(archived = true))
            val viewModel = open(7)

            assertTrue(viewModel.state.value.archived)

            viewModel.onArchiveToggle()
            advanceUntilIdle()

            assertEquals(listOf(7L), repository.observeActive().first().map { it.id })
        }

    @Test
    fun movingToTheTrashClosesTheScreenAfterTheNoteIsTrashed() =
        runTest {
            seed(note(7))
            val viewModel = open(7)

            viewModel.onTrash()
            advanceUntilIdle()

            assertTrue(viewModel.closed())
            assertEquals(listOf(7L), repository.observeTrashed().first().map { it.id })
        }

    @Test
    fun aFailedSaveKeepsTheScreenOpenWithTheTextAndSaysSo() =
        runTest {
            seed(note(7, "Seven"))
            val viewModel = open(7)
            repository.clear()
            viewModel.onTitleChange("Seven v2")

            viewModel.onSave()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(EditorMessage.SaveFailed, state.message)
            assertEquals("Seven v2", state.draft.title)
            assertFalse(state.isSaving)

            viewModel.onMessageShown()
            assertNull(viewModel.state.value.message)
        }

    @Test
    fun aFailedMoveToTheTrashKeepsTheScreenOpenAndSaysSo() =
        runTest {
            seed(note(7))
            val viewModel = open(7)
            repository.clear()

            viewModel.onTrash()
            advanceUntilIdle()

            assertEquals(EditorMessage.TrashFailed, viewModel.state.value.message)
            assertFalse(viewModel.state.value.isSaving)
        }

    // ---------------------------- tags ----------------------------

    @Test
    fun aCommaEndsATagAndTheRestStaysInTheField() =
        runTest {
            val viewModel = newNote()

            viewModel.onTagInputChange("work, ")

            assertEquals(listOf("work"), viewModel.state.value.draft.tags)
            assertEquals("", viewModel.state.value.tagInput)
        }

    @Test
    fun theDoneKeyAddsTheTypedTag() =
        runTest {
            val viewModel = newNote()
            viewModel.onTagInputChange("work")

            viewModel.onTagCommit()

            assertEquals(listOf("work"), viewModel.state.value.draft.tags)
        }

    @Test
    fun aTagThatDoesNotFitStaysInTheFieldWithAnError() =
        runTest {
            val viewModel = newNote()
            repeat(10) { viewModel.onTagInputChange("t$it,") }

            viewModel.onTagInputChange("eleventh")
            viewModel.onTagCommit()

            assertEquals(10, viewModel.state.value.draft.tags.size)
            assertEquals("eleventh", viewModel.state.value.tagInput)
            assertTrue(viewModel.state.value.tagLimitReached)

            viewModel.onTagRemove("t0")
            assertFalse(viewModel.state.value.tagLimitReached)
        }

    @Test
    fun pickingASuggestionAddsIt() =
        runTest {
            val viewModel = newNote()

            viewModel.onTagPicked("Home")

            assertEquals(listOf("Home"), viewModel.state.value.draft.tags)
            assertEquals("", viewModel.state.value.tagInput)
        }

    @Test
    fun suggestionsComeFromTheTagsOfExistingNotes() =
        runTest {
            seed(note(1, tags = listOf("Home", "Work")))
            val viewModel = newNote()
            advanceUntilIdle()

            viewModel.onTagInputChange("ho")

            assertEquals(listOf("Home"), viewModel.state.value.suggestions)
        }

    // ---------------------------- surviving recreation ----------------------------

    @Test
    fun aNewNotesDraftSurvivesTheScreenBeingRecreated() =
        runTest {
            val saved = route(EditorRoute.NEW_NOTE)
            newNote(saved).apply {
                onTitleChange("Draft")
                onContentChange("text")
                onTagInputChange("tag,")
                onPinToggle()
            }

            val restored = newNote(saved).state.value

            assertEquals(NoteDraft("Draft", "text", listOf("tag"), true), restored.draft)
        }

    @Test
    fun anExistingNotesDraftIsNotReplacedByTheSavedNoteWhenItLoads() =
        runTest {
            seed(note(7, "Seven"))
            val saved = route(7)
            open(7, saved).onTitleChange("Seven, edited")

            val restored = open(7, saved).state.value

            assertEquals("Seven, edited", restored.draft.title)
            assertTrue(restored.hasUnsavedChanges)
        }
}
