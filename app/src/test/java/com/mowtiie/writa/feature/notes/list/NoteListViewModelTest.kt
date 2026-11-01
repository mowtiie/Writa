package com.mowtiie.writa.feature.notes.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.mowtiie.writa.core.database.DirtyMask
import com.mowtiie.writa.core.datastore.PreferencesDataStores
import com.mowtiie.writa.core.datastore.SettingsStore
import com.mowtiie.writa.feature.notes.Note
import com.mowtiie.writa.feature.notes.NoteRepository
import com.mowtiie.writa.feature.notes.NotesFixture
import com.mowtiie.writa.feature.notes.RefreshFailure
import com.mowtiie.writa.feature.notes.Sort
import com.mowtiie.writa.feature.notes.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.QueueDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.Timeout
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.reflect.KClass

@OptIn(ExperimentalCoroutinesApi::class)
class NoteListViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val timeout: Timeout = Timeout.seconds(20)

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var fixture: NotesFixture
    private lateinit var repository: NoteRepository
    private lateinit var settings: SettingsStore

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
        fixture.close()
        Dispatchers.resetMain()
    }

    // ---------------------------- helpers ----------------------------

    private fun TestScope.viewModel(saved: SavedStateHandle = SavedStateHandle()): NoteListViewModel {
        if (!::settings.isInitialized) {
            settings =
                SettingsStore(
                    PreferencesDataStores.create(backgroundScope) { File(folder.root, "settings.preferences_pb") },
                )
        }
        val viewModel = NoteListViewModel(saved, repository, settings, fixture.scope, dispatcher)
        backgroundScope.launch(UnconfinedTestDispatcher(scheduler)) { viewModel.state.collect {} }
        return viewModel
    }

    private fun note(
        id: Long,
        title: String = "t$id",
        content: String? = "c$id",
        tags: List<String> = emptyList(),
        pinned: Boolean = false,
        archived: Boolean = false,
        deletedAt: String? = null,
    ) = Note(
        id,
        title,
        content,
        tags,
        pinned,
        archived,
        "2026-11-01T10:00:00+00:00",
        "2026-11-01T10:00:00+00:00",
        deletedAt,
    )

    private fun seed(
        note: Note,
        position: Long = note.id,
    ) = runBlocking { fixture.dao.insert(fixture.codec.encode(note, position)) }

    private suspend fun NoteListViewModel.loaded() = state.first { it.isLoaded }

    private suspend fun NoteListViewModel.titles() = loaded().items.map { it.note.title }

    private fun rowJson(id: Long) =
        """{"id":$id,"title":"server $id","content":"c","tags":[],"pinned":false,"archived":false,""" +
            """"created_at":"2026-11-01T10:00:00+00:00","updated_at":"2026-11-01T10:00:00+00:00",""" +
            """"deleted_at":null,"client_id":null}"""

    private suspend fun trashed() = repository.observeTrashed().first()

    // ---------------------------- what is listed ----------------------------

    @Test
    fun theListIsNotLoadedUntilTheNotesHaveBeenRead() =
        runTest {
            seed(note(1))
            val viewModel = viewModel()

            assertFalse(viewModel.state.value.isLoaded)
            assertTrue(
                viewModel.state.value.items
                    .isEmpty(),
            )
            assertEquals(listOf("t1"), viewModel.titles())
        }

    @Test
    fun theNewestNoteIsFirstAndPinnedNotesComeBeforeThem() =
        runTest {
            seed(note(1), position = 2)
            seed(note(2), position = 1)
            seed(note(3, pinned = true), position = 0)
            seed(note(4, pinned = true), position = 3)

            assertEquals(listOf("t3", "t4", "t2", "t1"), viewModel().titles())
        }

    @Test
    fun archivedAndTrashedNotesAreNotListed() =
        runTest {
            seed(note(1))
            seed(note(2, archived = true))
            seed(note(3, deletedAt = "2026-11-02T00:00:00Z"))

            assertEquals(listOf("t1"), viewModel().titles())
        }

    @Test
    fun anEmptyListSaysWhyItIsEmpty() =
        runTest {
            val viewModel = viewModel()
            assertEquals(EmptyReason.NoNotes, viewModel.state.first { it.isLoaded }.emptyReason)

            seed(note(1, title = "Shopping"))
            viewModel.state.first { it.items.isNotEmpty() }
            assertNull(viewModel.state.value.emptyReason)

            viewModel.onSearchChange("zzz")

            val state = viewModel.state.first { it.emptyReason != null }
            assertEquals(EmptyReason.NoMatchForSearch("zzz"), state.emptyReason)
            assertTrue(state.items.isEmpty())
        }

    @Test
    fun aNoteKeepsItsKeyWhenAnUploadGivesItTheServersId() =
        runTest {
            val viewModel = viewModel()
            val created = repository.create("Idea", "body", emptyList(), pinned = false)
            assertEquals(
                created.id,
                viewModel.state
                    .first { it.items.isNotEmpty() }
                    .items
                    .single()
                    .key,
            )

            fixture.cache.syncSucceeded(created.id, version = 1, server = note(100, title = "Idea"))

            val item =
                viewModel.state
                    .first {
                        it.items
                            .singleOrNull()
                            ?.note
                            ?.id == 100L
                    }.items
                    .single()
            assertEquals(created.id, item.key)
            assertEquals(100L, item.note.id)
        }

    // ---------------------------- refreshing ----------------------------

    @Test
    fun theNotesAreRefreshedOnceWhenTheScreenIsOpened() =
        runTest {
            server.enqueue(MockResponse().setBody("[${rowJson(7)}]"))
            val viewModel = viewModel()

            assertEquals(
                listOf("server 7"),
                viewModel.state
                    .first { it.items.isNotEmpty() }
                    .items
                    .map { it.note.title },
            )
            assertEquals(1, server.requestCount)
            assertNull(viewModel.state.first { !it.isRefreshing }.banner)
        }

    @Test
    fun aFailedRefreshShowsABannerAndTheSavedNotes() =
        runTest {
            seed(note(1))
            val viewModel = viewModel()

            val state = viewModel.state.first { it.banner != null }

            assertEquals(NotesBanner.RefreshFailed(RefreshFailure.Server), state.banner)
            assertEquals(listOf("t1"), state.items.map { it.note.title })
        }

    @Test
    fun beingOfflineWithUnsentChangesSaysHowManyWillUpload() =
        runTest {
            seed(note(1))
            repository.setPinned(1, true)
            repository.create("new", null, emptyList(), pinned = false)
            server.shutdown()
            val viewModel = viewModel()

            assertEquals(NotesBanner.UnsentChanges(2), viewModel.state.first { it.banner != null }.banner)
        }

    @Test
    fun refreshingAgainClearsTheBannerOnceTheServerAnswers() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.first { it.banner != null }

            server.enqueue(MockResponse().setBody("[${rowJson(7)}]"))
            viewModel.refresh()

            assertNull(viewModel.state.first { it.banner == null && it.items.isNotEmpty() }.banner)
        }

    // ---------------------------- search ----------------------------

    @Test
    fun searchFiltersByTitleContentAndTagsAndClosingForgetsIt() =
        runTest {
            seed(note(1, title = "Shopping", content = "milk"), position = 0)
            seed(note(2, title = "Work", content = "report", tags = listOf("Office")), position = 1)
            val viewModel = viewModel()
            assertEquals(2, viewModel.loaded().items.size)

            viewModel.onSearchOpen()
            viewModel.onSearchChange("MILK")
            assertEquals(
                listOf("Shopping"),
                viewModel.state
                    .first { it.items.size == 1 }
                    .items
                    .map { it.note.title },
            )

            viewModel.onSearchChange("office")
            assertEquals(
                listOf("Work"),
                viewModel.state
                    .first {
                        it.items
                            .singleOrNull()
                            ?.note
                            ?.title == "Work"
                    }.items
                    .map { it.note.title },
            )

            viewModel.onSearchClose()
            val closed = viewModel.state.first { it.items.size == 2 }
            assertFalse(closed.isSearchOpen)
            assertEquals("", closed.search)
        }

    @Test
    fun theSearchSurvivesTheProcessBeingKilled() =
        runTest {
            seed(note(1, title = "Shopping"))
            seed(note(2, title = "Work"), position = 1)
            val saved = SavedStateHandle()
            val first = viewModel(saved)
            first.onSearchOpen()
            first.onSearchChange("work")

            val restored = viewModel(saved)

            val state = restored.state.first { it.isLoaded }
            assertTrue(state.isSearchOpen)
            assertEquals("work", state.search)
            assertEquals(listOf("Work"), state.items.map { it.note.title })
        }

    // ---------------------------- tags ----------------------------

    @Test
    fun theFilterBarListsEveryTagWithItsCountMostUsedFirst() =
        runTest {
            seed(note(1, tags = listOf("work", "home")), position = 0)
            seed(note(2, tags = listOf("Work")), position = 1)
            seed(note(3, tags = listOf("zeta")), position = 2)

            val tags = viewModel().loaded().tags

            assertEquals(listOf("work" to 2, "home" to 1, "zeta" to 1), tags.map { it.tag to it.count })
            assertTrue(tags.none { it.selected })
        }

    @Test
    fun selectedTagsShowNotesWithAnyOfThem() =
        runTest {
            seed(note(1, tags = listOf("work")), position = 0)
            seed(note(2, tags = listOf("home")), position = 1)
            seed(note(3, tags = listOf("other")), position = 2)
            val viewModel = viewModel()
            viewModel.loaded()

            viewModel.onTagToggle("work")
            assertEquals(
                listOf("t1"),
                viewModel.state
                    .first { it.items.size == 1 }
                    .items
                    .map { it.note.title },
            )

            viewModel.onTagToggle("home")
            val both = viewModel.state.first { it.items.size == 2 }
            assertEquals(listOf("t1", "t2"), both.items.map { it.note.title })
            assertEquals(
                setOf("work", "home"),
                both.tags
                    .filter { it.selected }
                    .map { it.key }
                    .toSet(),
            )

            viewModel.onClearTags()
            assertEquals(
                3,
                viewModel.state
                    .first { it.items.size == 3 }
                    .items.size,
            )
        }

    @Test
    fun theSelectedTagsSurviveTheProcessBeingKilled() =
        runTest {
            seed(note(1, tags = listOf("work")), position = 0)
            seed(note(2, tags = listOf("home")), position = 1)
            val saved = SavedStateHandle()
            viewModel(saved).onTagToggle("home")

            val restored = viewModel(saved)

            assertEquals(
                listOf("t2"),
                restored.state
                    .first { it.items.size == 1 }
                    .items
                    .map { it.note.title },
            )
        }

    @Test
    fun aTagThatNoNoteHasAnyMoreIsNoLongerSelected() =
        runTest {
            seed(note(1, tags = listOf("work")), position = 0)
            seed(note(2, tags = listOf("home")), position = 1)
            val saved = SavedStateHandle()
            val viewModel = viewModel(saved)
            viewModel.loaded()
            viewModel.onTagToggle("work")
            viewModel.state.first { it.items.size == 1 }

            repository.moveToTrash(1)
            viewModel.state.first { it.tags.none { tag -> tag.key == "work" } }
            seed(note(3, tags = listOf("work")), position = 2)

            val state = viewModel.state.first { it.tags.any { tag -> tag.key == "work" } }
            assertTrue("the tag must not come back selected", state.tags.none { it.selected })
            assertEquals(2, state.items.size)
        }

    // ---------------------------- sort ----------------------------

    @Test
    fun theChosenSortIsAppliedKeptAndPinnedNotesStayOnTop() =
        runTest {
            seed(note(1, title = "banana"), position = 0)
            seed(note(2, title = "Apple"), position = 1)
            seed(note(3, title = "cherry", pinned = true), position = 2)
            val viewModel = viewModel()
            assertEquals(listOf("cherry", "banana", "Apple"), viewModel.titles())

            viewModel.onSortSelected(Sort.TITLE_AZ)

            val sorted = viewModel.state.first { it.sort == Sort.TITLE_AZ }
            assertEquals(listOf("cherry", "Apple", "banana"), sorted.items.map { it.note.title })
            assertEquals(Sort.TITLE_AZ, settings.settings.first { it.sort == Sort.TITLE_AZ }.sort)
        }

    // ---------------------------- swiping a note away ----------------------------

    @Test
    fun aSwipedNoteIsHiddenAtOnceWhileItsUndoPeriodRuns() =
        runTest {
            seed(note(1), position = 0)
            seed(note(2), position = 1)
            val viewModel = viewModel()
            viewModel.loaded()

            viewModel.onSwipeDelete(1)
            runCurrent()

            val state = viewModel.state.value
            assertEquals(listOf("t2"), state.items.map { it.note.title })
            assertEquals(PendingDelete(1), state.pendingDelete)
            assertTrue(trashed().isEmpty())
        }

    @Test
    fun undoBringsTheNoteBackAndNothingIsTrashed() =
        runTest {
            seed(note(1))
            val viewModel = viewModel()
            viewModel.loaded()
            viewModel.onSwipeDelete(1)
            runCurrent()

            viewModel.onUndo()
            runCurrent()
            scheduler.advanceTimeBy(NoteListViewModel.UNDO_DURATION_MS * 2)
            runCurrent()

            val state = viewModel.state.value
            assertEquals(listOf("t1"), state.items.map { it.note.title })
            assertNull(state.pendingDelete)
            assertTrue(trashed().isEmpty())
        }

    @Test
    fun anOldUndoTimerNeverCutsTheNextUndoPeriodShort() =
        runTest {
            seed(note(1), position = 0)
            seed(note(2), position = 1)
            val viewModel = viewModel()
            viewModel.loaded()
            val period = NoteListViewModel.UNDO_DURATION_MS

            viewModel.onSwipeDelete(1)
            runCurrent()
            scheduler.advanceTimeBy(period / 2)
            viewModel.onUndo()
            viewModel.onSwipeDelete(2)
            runCurrent()

            // The first timer would have ended here, in the middle of the second note's undo period.
            scheduler.advanceTimeBy(period / 2 + period / 4)
            runCurrent()
            assertEquals(PendingDelete(2), viewModel.state.value.pendingDelete)
            assertTrue(trashed().isEmpty())

            scheduler.advanceTimeBy(period / 4)
            runCurrent()
            assertEquals(listOf("t2"), trashed().map { it.title })
        }

    @Test
    fun theFirstTimerOfASwipedNoteDoesNotCutTheNextOneShortEither() =
        runTest {
            seed(note(1), position = 0)
            seed(note(2), position = 1)
            val viewModel = viewModel()
            viewModel.loaded()
            val period = NoteListViewModel.UNDO_DURATION_MS

            viewModel.onSwipeDelete(1)
            runCurrent()
            scheduler.advanceTimeBy(period / 2)
            viewModel.onSwipeDelete(2)
            runCurrent()

            scheduler.advanceTimeBy(period / 2 + period / 4)
            runCurrent()
            assertEquals(PendingDelete(2), viewModel.state.value.pendingDelete)
        }

    @Test
    fun theNoteGoesToTheTrashWhenTheUndoPeriodEnds() =
        runTest {
            seed(note(1))
            val viewModel = viewModel()
            viewModel.loaded()
            viewModel.onSwipeDelete(1)
            runCurrent()

            scheduler.advanceTimeBy(NoteListViewModel.UNDO_DURATION_MS - 1)
            runCurrent()
            assertNotNull("still undoable just before the end", viewModel.state.value.pendingDelete)
            assertTrue(trashed().isEmpty())

            scheduler.advanceTimeBy(1)
            runCurrent()

            assertNull(viewModel.state.value.pendingDelete)
            assertEquals(listOf("t1"), trashed().map { it.title })
            assertTrue(
                viewModel.state.value.items
                    .isEmpty(),
            )
        }

    @Test
    fun aNoteNeverFlashesBackBetweenTheEndOfTheUndoPeriodAndTheTrash() =
        runTest {
            seed(note(1))
            val viewModel = viewModel()
            val seen = mutableListOf<NoteListUiState>()
            backgroundScope.launch(UnconfinedTestDispatcher(scheduler)) { viewModel.state.collect { seen += it } }
            viewModel.loaded()
            viewModel.onSwipeDelete(1)
            runCurrent()

            scheduler.advanceTimeBy(NoteListViewModel.UNDO_DURATION_MS)
            runCurrent()
            viewModel.state.first { trashed().isNotEmpty() }

            val afterSwipe = seen.dropWhile { it.pendingDelete == null }
            assertTrue(afterSwipe.isNotEmpty())
            assertTrue("the swiped note came back", afterSwipe.none { state -> state.items.any { it.note.id == 1L } })
        }

    @Test
    fun swipingAnotherNoteSendsTheFirstOneToTheTrashAtOnce() =
        runTest {
            seed(note(1), position = 0)
            seed(note(2), position = 1)
            val viewModel = viewModel()
            viewModel.loaded()

            viewModel.onSwipeDelete(1)
            viewModel.onSwipeDelete(2)
            runCurrent()

            assertEquals(listOf("t1"), trashed().map { it.title })
            assertEquals(PendingDelete(2), viewModel.state.value.pendingDelete)
            assertTrue(
                viewModel.state.value.items
                    .isEmpty(),
            )
        }

    @Test
    fun leavingTheScreenSendsTheWaitingNoteToTheTrash() =
        runTest {
            seed(note(1))
            val viewModel = viewModel()
            viewModel.loaded()
            viewModel.onSwipeDelete(1)
            runCurrent()

            viewModel.onScreenStopped()
            runCurrent()

            assertEquals(listOf("t1"), trashed().map { it.title })
            assertNull(viewModel.state.value.pendingDelete)
        }

    @Test
    fun stoppingTheScreenWithNothingWaitingDoesNothing() =
        runTest {
            seed(note(1))
            val viewModel = viewModel()
            viewModel.loaded()

            viewModel.onScreenStopped()
            runCurrent()

            assertTrue(trashed().isEmpty())
            assertEquals(1, viewModel.state.value.items.size)
        }

    @Test
    fun theScreenGoingAwayForGoodStillTrashesTheWaitingNote() =
        runTest {
            seed(note(1))
            val store = ViewModelStore()
            val viewModel =
                ViewModelProvider(
                    store,
                    factoryOf(NoteListViewModel::class) {
                        viewModel()
                    },
                )[NoteListViewModel::class.java]
            viewModel.loaded()
            viewModel.onSwipeDelete(1)
            runCurrent()

            store.clear()

            withTimeout(WAIT_MS) {
                while (trashed().isEmpty()) kotlinx.coroutines.delay(20)
            }
            assertEquals(listOf("t1"), trashed().map { it.title })
        }

    @Test
    fun aFailedTrashBringsTheNoteBackWithAMessage() =
        runTest {
            seed(note(1))
            val viewModel = viewModel()
            viewModel.loaded()
            viewModel.onSwipeDelete(1)
            runCurrent()
            fixture.crypto.failToEncrypt = true

            viewModel.onScreenStopped()
            runCurrent()

            assertEquals(NoteListMessage.ActionFailed, viewModel.state.first { it.message != null }.message)
            assertEquals(
                listOf("t1"),
                viewModel.state
                    .first { it.items.isNotEmpty() }
                    .items
                    .map { it.note.title },
            )
        }

    // ---------------------------- actions on a note ----------------------------

    @Test
    fun pinningArchivingAndTrashingChangeTheList() =
        runTest {
            seed(note(1), position = 0)
            seed(note(2), position = 1)
            seed(note(3), position = 2)
            val viewModel = viewModel()
            viewModel.loaded()

            viewModel.setPinned(3, true)
            assertEquals(
                listOf("t3", "t1", "t2"),
                viewModel.state
                    .first {
                        it.items
                            .firstOrNull()
                            ?.note
                            ?.id == 3L
                    }.items
                    .map { it.note.title },
            )

            viewModel.archive(1)
            assertEquals(
                listOf("t3", "t2"),
                viewModel.state
                    .first { it.items.size == 2 }
                    .items
                    .map { it.note.title },
            )
            assertEquals(listOf("t1"), repository.observeArchived().first().map { it.title })

            viewModel.moveToTrash(2)
            assertEquals(
                listOf("t3"),
                viewModel.state
                    .first { it.items.size == 1 }
                    .items
                    .map { it.note.title },
            )
            assertEquals(listOf("t2"), trashed().map { it.title })
        }

    @Test
    fun aFailedNoteCanBeRetriedOrDiscarded() =
        runTest {
            seed(note(1)) { it.copy(dirtyMask = DirtyMask.TEXT, syncFailed = true) }
            seed(note(2), position = 1) { it.copy(dirtyMask = DirtyMask.TEXT, syncFailed = true) }
            val viewModel = viewModel()
            assertEquals(
                SyncState.Failed,
                viewModel
                    .loaded()
                    .items
                    .first()
                    .note.syncState,
            )

            viewModel.retryFailed(1)
            viewModel.state.first { state ->
                state.items
                    .first { it.note.id == 1L }
                    .note.syncState == SyncState.Pending
            }

            viewModel.discardFailed(2)
            assertEquals(
                listOf(1L),
                viewModel.state
                    .first { it.items.size == 1 }
                    .items
                    .map { it.note.id },
            )
        }

    @Test
    fun anActionThatCannotBeWrittenSaysSoOnce() =
        runTest {
            seed(note(1))
            val viewModel = viewModel()
            viewModel.loaded()
            fixture.crypto.failToEncrypt = true

            viewModel.archive(1)

            assertEquals(NoteListMessage.ActionFailed, viewModel.state.first { it.message != null }.message)
            viewModel.onMessageShown()
            assertNull(viewModel.state.first { it.message == null }.message)
        }

    private fun seed(
        note: Note,
        position: Long = note.id,
        change: (com.mowtiie.writa.core.database.NoteEntity) -> com.mowtiie.writa.core.database.NoteEntity,
    ) = runBlocking { fixture.dao.insert(change(fixture.codec.encode(note, position))) }

    private fun <T : ViewModel> factoryOf(
        type: KClass<T>,
        create: () -> T,
    ) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
    }

    private companion object {
        const val WAIT_MS = 10_000L
    }
}
