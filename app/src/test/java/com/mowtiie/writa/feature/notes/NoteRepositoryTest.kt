package com.mowtiie.writa.feature.notes

import app.cash.turbine.test
import com.mowtiie.writa.core.database.DirtyMask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class NoteRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val clock = Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC)
    private lateinit var fixture: NotesFixture
    private lateinit var repository: NoteRepository

    private val server get() = fixture.server
    private val dao get() = fixture.dao
    private val cache get() = fixture.cache

    @Before
    fun setUp() {
        fixture = NotesFixture(folder.root, clock).also { it.start() }
        // Uploads that the tests do not script fail quickly instead of waiting for an answer that never comes.
        (server.dispatcher as QueueDispatcher).setFailFast(MockResponse().setResponseCode(503))
        repository = NoteRepository(fixture.cache, fixture.backend.remote, fixture.engine, clock)
    }

    @After
    fun tearDown() = fixture.close()

    // ---------------------------- helpers ----------------------------

    private fun row(
        id: Long,
        title: String = "t$id",
        tags: String = "[]",
        pinned: Boolean = false,
        archived: Boolean = false,
        deletedAt: String? = null,
        updatedAt: String = T1,
    ) = """{"id":$id,"title":"$title","content":"c$id","tags":$tags,"pinned":$pinned,"archived":$archived,""" +
        """"created_at":"$T0","updated_at":"$updatedAt","deleted_at":${deletedAt?.let {
            "\"$it\""
        }},"client_id":null}"""

    private fun list(vararg rows: String) =
        MockResponse().setResponseCode(200).setBody(rows.joinToString(prefix = "[", postfix = "]"))

    private fun note(
        id: Long,
        title: String = "t$id",
        tags: List<String> = emptyList(),
    ) = Note(id, title, "c$id", tags, false, false, T0, T1, null)

    private fun seedSynced(
        id: Long,
        title: String = "t$id",
        tags: List<String> = emptyList(),
        archived: Boolean = false,
        deletedAt: String? = null,
    ) = runBlocking {
        dao.insert(fixture.codec.encode(note(id, title, tags).copy(archived = archived, deletedAt = deletedAt), id))
    }

    private fun raw(id: Long) = runBlocking { dao.get(id) }

    private fun refresh() = runBlocking { repository.refresh() }

    private fun active() = runBlocking { repository.observeActive().first() }

    private fun archived() = runBlocking { repository.observeArchived().first() }

    private fun trashed() = runBlocking { repository.observeTrashed().first() }

    private fun pending() = runBlocking { cache.pending() }

    private fun awaitRequest(): RecordedRequest =
        server.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS) ?: error("no request arrived")

    private fun awaitCount(count: Int) =
        runBlocking {
            withTimeout(WAIT_MS) {
                while (server.requestCount <
                    count
                ) {
                    delay(POLL_MS)
                }
            }
        }

    // ---------------------------- refreshing ----------------------------

    @Test
    fun refreshStoresEveryNoteIncludingArchivedAndTrashedOnes() {
        server.enqueue(list(row(3), row(2, archived = true), row(1, deletedAt = "2026-11-02T00:00:00Z")))

        refresh()

        assertEquals(listOf(3L), active().map { it.id })
        assertEquals(listOf(2L), archived().map { it.id })
        assertEquals(listOf(1L), trashed().map { it.id })
        assertEquals(RefreshState.Idle, repository.refreshState.value)
    }

    @Test
    fun refreshReadsEveryPageOfALargeTable() {
        val full = List(200) { row(1000L - it) }
        server.enqueue(list(*full.toTypedArray()))
        server.enqueue(list(row(5), row(4), row(3)))

        refresh()

        assertEquals(203, active().size)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun aFailureWhileReadingLaterPagesLeavesTheSavedNotesUntouched() {
        seedSynced(1, title = "saved")
        server.enqueue(list(*List(200) { row(1000L - it) }.toTypedArray()))

        refresh()

        assertEquals(RefreshState.Failed(RefreshFailure.Server), repository.refreshState.value)
        assertEquals(listOf("saved"), active().map { it.title })
    }

    @Test
    fun aRefreshWhileARefusedUploadIsStillBeingAnsweredSharesItsPassAndKeepsTheNote() {
        server.enqueue(MockResponse().setResponseCode(503).setHeadersDelay(SLOW_ANSWER_MS, TimeUnit.MILLISECONDS))
        val offline = runBlocking { repository.create("mine", "body", emptyList(), pinned = false) }
        awaitCount(1)
        server.takeRequest()
        server.enqueue(list(row(77)))

        refresh()

        assertEquals("GET", awaitRequest().method)
        assertEquals(RefreshState.Idle, repository.refreshState.value)
        assertEquals(setOf(offline.id, 77L), active().map { it.id }.toSet())
        assertEquals(SyncState.Pending, active().single { it.id == offline.id }.syncState)
    }

    @Test
    fun refreshReplacesSyncedNotesAndDropsTheOnesTheServerNoLongerHas() {
        seedSynced(1, title = "old")
        seedSynced(2)
        server.enqueue(list(row(1, title = "new")))

        refresh()

        assertEquals(listOf("new"), active().map { it.title })
        assertNull(raw(2))
    }

    @Test
    fun refreshKeepsChangesThatCouldNotBeUploadedYet() {
        seedSynced(5)
        runBlocking { repository.setPinned(5, true) }
        val offline = runBlocking { repository.create("mine", "body", emptyList(), pinned = false) }
        server.enqueue(MockResponse().setResponseCode(500)) // the upload before the refresh fails
        server.enqueue(list(row(5, title = "server copy")))

        refresh()

        val shown = active().associateBy { it.id }
        assertEquals("t5", shown.getValue(5).title)
        assertTrue(shown.getValue(5).pinned)
        assertEquals("mine", shown.getValue(offline.id).title)
        assertEquals(SyncState.Pending, shown.getValue(offline.id).syncState)
    }

    @Test
    fun refreshUploadsWhatIsWaitingBeforeItFetches() {
        val offline = runBlocking { repository.create("mine", "body", emptyList(), pinned = false) }
        awaitCount(1) // the create above already started an upload, which the fail-fast answer refuses
        server.takeRequest()
        // Let that refused attempt finish: a refresh that starts while it is still being answered shares its pass.
        runBlocking { fixture.engine.sync() }
        repeat(server.requestCount - 1) { server.takeRequest() }
        server.enqueue(list(row(55, title = "mine")))
        server.enqueue(list(row(55, title = "mine")))

        refresh()

        val methods =
            generateSequence {
                server.takeRequest(
                    SHORT_WAIT_MS,
                    TimeUnit.MILLISECONDS,
                )
            }.map { it.method }.toList()
        assertEquals(listOf("POST", "GET"), methods)
        assertNull(raw(offline.id))
        assertEquals(listOf(55L), active().map { it.id })
        assertEquals(SyncState.Synced, active().single().syncState)
    }

    @Test
    fun uploadsWaitWhileTheRefreshFetchesAndMerges() {
        seedSynced(5)
        val getStarted = AtomicBoolean(false)
        val getFinishedAt = AtomicLong(0)
        val patchArrivedAt = AtomicLong(0)
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.method == "GET") {
                        getStarted.set(true)
                        Thread.sleep(SLOW_MS)
                        getFinishedAt.set(System.nanoTime())
                    } else {
                        patchArrivedAt.set(System.nanoTime())
                    }
                    return list(row(5))
                }
            }

        runBlocking {
            val refreshing = launch(Dispatchers.Default) { repository.refresh() }
            withTimeout(WAIT_MS) { while (!getStarted.get()) delay(POLL_MS) }
            repository.setPinned(5, true)
            refreshing.join()
            withTimeout(WAIT_MS) { while (patchArrivedAt.get() == 0L) delay(POLL_MS) }
        }

        assertTrue("the upload started before the merge was done", patchArrivedAt.get() >= getFinishedAt.get())
    }

    @Test
    fun theRefreshStateGoesIdleLoadingIdle() {
        server.enqueue(list(row(1)).setHeadersDelay(SLOW_MS, TimeUnit.MILLISECONDS))

        runBlocking {
            repository.refreshState.test {
                assertEquals(RefreshState.Idle, awaitItem())
                val job = launch(Dispatchers.Default) { repository.refresh() }
                assertEquals(RefreshState.Loading, awaitItem())
                assertEquals(RefreshState.Idle, awaitItem())
                job.join()
            }
        }
    }

    @Test
    fun anUnreachableServerIsReportedAndTheSavedNotesStay() {
        seedSynced(1)
        server.shutdown()

        refresh()

        assertEquals(RefreshState.Failed(RefreshFailure.Offline), repository.refreshState.value)
        assertEquals(listOf(1L), active().map { it.id })
    }

    @Test
    fun aServerErrorIsReported() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"message":"boom"}"""))

        refresh()

        assertEquals(RefreshState.Failed(RefreshFailure.Server), repository.refreshState.value)
    }

    @Test
    fun anOutdatedSchemaIsReportedLeavesTheCacheAloneAndRecoversOnceFixed() {
        seedSynced(1)
        server.enqueue(MockResponse().setResponseCode(200).setBody("""[{"id":9,"title":"t","content":"c"}]"""))

        refresh()

        assertEquals(RefreshState.Failed(RefreshFailure.SchemaOutdated), repository.refreshState.value)
        assertEquals(listOf(1L), active().map { it.id })

        server.enqueue(list(row(9)))
        refresh()

        assertEquals(RefreshState.Idle, repository.refreshState.value)
        assertEquals(listOf(9L), active().map { it.id })
    }

    @Test
    fun aSignedOutCacheMakesTheRefreshUnavailable() {
        fixture.owner.value = null
        server.enqueue(list(row(1)))

        refresh()

        assertEquals(RefreshState.Failed(RefreshFailure.Unavailable), repository.refreshState.value)
    }

    @Test
    fun aRefreshThatIsAlreadyRunningIsNotStartedTwice() {
        server.enqueue(list(row(1)).setHeadersDelay(SLOW_MS, TimeUnit.MILLISECONDS))

        runBlocking {
            listOf(
                launch(Dispatchers.Default) {
                    repository.refresh()
                },
                launch(Dispatchers.Default) { repository.refresh() },
            ).joinAll()
        }

        assertEquals(1, server.requestCount)
        assertEquals(RefreshState.Idle, repository.refreshState.value)
    }

    @Test
    fun aCancelledRefreshWritesNothingAndLeavesNoLoadingState() {
        seedSynced(1)
        server.enqueue(list(row(9)).setHeadersDelay(SLOW_HEADERS_MS, TimeUnit.MILLISECONDS))

        runBlocking {
            val job = launch(Dispatchers.Default) { repository.refresh() }
            withTimeout(WAIT_MS) { while (server.requestCount == 0) delay(POLL_MS) }
            job.cancel()
            job.join()
        }

        assertEquals(RefreshState.Idle, repository.refreshState.value)
        assertEquals(listOf(1L), active().map { it.id })
    }

    // ---------------------------- creating and editing ----------------------------

    @Test
    fun aNewNoteAppearsAtOnceAndIsUploadedInTheBackground() {
        val created = runBlocking { repository.create("Idea", "body", listOf("#work"), pinned = true) }

        assertTrue(created.isLocalOnly)
        assertEquals(listOf("work"), created.tags)
        assertEquals(listOf(created.id), active().map { it.id })
        assertEquals("POST", awaitRequest().method)
    }

    @Test
    fun aNewNoteStaysWhenTheUploadFails() {
        val created = runBlocking { repository.create("Idea", null, emptyList(), pinned = false) }
        awaitRequest()

        assertEquals(listOf(created.id), active().map { it.id })
        assertEquals(1, pending().size)
    }

    @Test
    fun savingMarksOnlyWhatChanged() {
        seedSynced(5)
        val stored = runBlocking { repository.get(5) }!!

        runBlocking { repository.save(stored.copy(title = "New title")) }
        assertEquals(DirtyMask.TEXT, raw(5)!!.dirtyMask)

        runBlocking { repository.save(stored.copy(title = "New title", tags = listOf("a"), pinned = true)) }
        assertEquals(DirtyMask.TEXT or DirtyMask.TAGS or DirtyMask.PINNED, raw(5)!!.dirtyMask)
        assertEquals("PATCH", awaitRequest().method)
    }

    @Test
    fun savingTheSameNoteWritesNothingAndUploadsNothing() {
        seedSynced(5)
        val stored = runBlocking { repository.get(5) }!!

        val saved = runBlocking { repository.save(stored) }

        assertEquals(stored, saved)
        assertEquals(0, raw(5)!!.dirtyMask)
        assertEquals(0L, raw(5)!!.version)
        assertNull(server.takeRequest(SHORT_WAIT_MS, TimeUnit.MILLISECONDS))
    }

    @Test
    fun savingAnUnknownNoteGivesNull() {
        assertNull(runBlocking { repository.save(note(404)) })
    }

    @Test
    fun savedTagsAreCleanedUp() {
        seedSynced(5)

        val saved = runBlocking { repository.save(note(5, tags = listOf(" #Work ", "work", "Home"))) }

        assertEquals(listOf("Work", "Home"), saved?.tags)
    }

    @Test
    fun pinningAndArchivingChangeOnlyTheirOwnFlag() {
        seedSynced(5)

        runBlocking { repository.setPinned(5, true) }
        assertEquals(DirtyMask.PINNED, raw(5)!!.dirtyMask)
        assertTrue(raw(5)!!.pinned)

        runBlocking { repository.setArchived(5, true) }
        assertEquals(DirtyMask.PINNED or DirtyMask.ARCHIVED, raw(5)!!.dirtyMask)
        assertEquals(listOf(5L), archived().map { it.id })
        assertTrue(active().isEmpty())
    }

    @Test
    fun trashingStampsTheTimeAndMovesTheNoteToTheTrash() {
        seedSynced(5)

        val trashed = runBlocking { repository.moveToTrash(5) }

        assertEquals(NOW, trashed?.deletedAt)
        assertEquals(DirtyMask.TRASH, raw(5)!!.dirtyMask)
        assertTrue(active().isEmpty())
        assertEquals(listOf(5L), trashed().map { it.id })
    }

    @Test
    fun trashingAnAlreadyTrashedNoteChangesNothing() {
        seedSynced(5, deletedAt = "2026-11-02T00:00:00Z")

        runBlocking { repository.moveToTrash(5) }

        assertEquals(0, raw(5)!!.dirtyMask)
        assertEquals("2026-11-02T00:00:00Z", raw(5)!!.deletedAt)
    }

    @Test
    fun restoringBringsTheNoteBackAndKeepsItsArchiveFlag() {
        seedSynced(5, archived = true, deletedAt = "2026-11-02T00:00:00Z")

        runBlocking { repository.restore(5) }

        assertNull(raw(5)!!.deletedAt)
        assertEquals(DirtyMask.TRASH, raw(5)!!.dirtyMask)
        assertEquals(listOf(5L), archived().map { it.id })
    }

    @Test
    fun theHelpersGiveNullForAnUnknownNote() {
        assertNull(runBlocking { repository.setPinned(404, true) })
        assertNull(runBlocking { repository.setArchived(404, true) })
        assertNull(runBlocking { repository.moveToTrash(404) })
        assertNull(runBlocking { repository.restore(404) })
    }

    // ---------------------------- deleting and failed notes ----------------------------

    @Test
    fun deletingForGoodHidesTheNoteAndUploadsTheDelete() {
        seedSynced(5)

        assertTrue(runBlocking { repository.deletePermanently(5) })

        assertTrue(active().isEmpty())
        assertEquals("DELETE", awaitRequest().method)
    }

    @Test
    fun emptyingTheTrashDeletesOnlyTheTrashedNotes() {
        seedSynced(1)
        seedSynced(2, deletedAt = T1)
        seedSynced(3, archived = true)
        seedSynced(4, deletedAt = T1)

        assertTrue(runBlocking { repository.emptyTrash() })

        assertTrue(trashed().isEmpty())
        assertEquals(listOf(1L), active().map { it.id })
        assertEquals(listOf(3L), archived().map { it.id })
    }

    @Test
    fun emptyingAnEmptyTrashIsFine() {
        assertTrue(runBlocking { repository.emptyTrash() })
    }

    @Test
    fun deletingANoteThatNeverReachedTheServerSendsNothing() {
        val created = runBlocking { repository.create("t", null, emptyList(), pinned = false) }
        awaitRequest()

        assertTrue(runBlocking { repository.deletePermanently(created.id) })

        assertNull(raw(created.id))
        assertFalse(runBlocking { repository.deletePermanently(created.id) })
    }

    @Test
    fun aFailedNoteCanBeRetriedOrDiscarded() {
        seedSynced(5)
        runBlocking {
            repository.setPinned(5, true)
            cache.syncFailed(5)
        }
        assertEquals(SyncState.Failed, runBlocking { repository.get(5) }?.syncState)

        runBlocking { repository.retryFailed(5) }
        assertNotNull(awaitRequest())
        assertFalse(raw(5)!!.syncFailed)

        runBlocking { cache.syncFailed(5) }
        runBlocking { repository.discardFailed(5) }
        assertNull(raw(5))
    }

    // ---------------------------- the rest ----------------------------

    @Test
    fun knownTagsComeFromExistingNotesMostUsedFirstAndNotFromTheTrash() {
        seedSynced(1, tags = listOf("work", "home"))
        seedSynced(2, tags = listOf("Work"))
        seedSynced(3, tags = listOf("archive-only"), archived = true)
        seedSynced(4, tags = listOf("trashed-only"), deletedAt = "2026-11-02T00:00:00Z")

        val tags = runBlocking { repository.observeKnownTags().first() }

        assertEquals(listOf("work", "archive-only", "home"), tags)
    }

    @Test
    fun theKnownTagsFollowTheNotes() {
        seedSynced(1, tags = listOf("a"))

        runBlocking {
            repository.observeKnownTags().test {
                assertEquals(listOf("a"), awaitItem())
                repository.save(note(1, tags = listOf("b")))
                assertEquals(listOf("b"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun thePendingCountCoversUnsentWork() {
        seedSynced(1)

        runBlocking {
            repository.observePendingCount().test {
                assertEquals(0, awaitItem())
                repository.setPinned(1, true)
                assertEquals(1, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun aTemporaryIdResolvesToTheUploadedNote() {
        val created = runBlocking { repository.create("t", null, emptyList(), pinned = false) }
        awaitRequest()
        server.enqueue(list(row(55, title = "t")))
        runBlocking { fixture.engine.sync() }

        assertEquals(55L, runBlocking { repository.resolveId(created.id) })
        assertEquals(55L, runBlocking { repository.get(created.id) }?.id)
    }

    @Test
    fun clearForgetsEverythingIncludingUnsentChanges() {
        seedSynced(1)
        runBlocking { repository.create("t", null, emptyList(), pinned = false) }

        runBlocking { repository.clear() }

        assertTrue(active().isEmpty())
        assertEquals(0, runBlocking { repository.observePendingCount().first() })
    }

    private companion object {
        const val NOW = "2026-11-05T12:00:00Z"
        const val T0 = "2026-11-01T10:00:00+00:00"
        const val T1 = "2026-11-01T11:00:00+00:00"
        const val WAIT_SECONDS = 5L
        const val WAIT_MS = 10_000L
        const val POLL_MS = 20L
        const val SLOW_MS = 300L
        const val SLOW_ANSWER_MS = 400L
        const val SLOW_HEADERS_MS = 2_000L
        const val SHORT_WAIT_MS = 300L
    }
}
