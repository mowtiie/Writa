package com.mowtiie.writa.feature.notes.sync

import com.mowtiie.writa.core.database.CacheMeta
import com.mowtiie.writa.core.database.DirtyMask
import com.mowtiie.writa.core.database.FakeNoteDao
import com.mowtiie.writa.core.database.PassThroughTransactionRunner
import com.mowtiie.writa.core.network.TestBackend
import com.mowtiie.writa.core.network.apiJson
import com.mowtiie.writa.core.security.FakeNoteCrypto
import com.mowtiie.writa.feature.notes.Note
import com.mowtiie.writa.feature.notes.NoteCodec
import com.mowtiie.writa.feature.notes.NotesFixture
import com.mowtiie.writa.feature.notes.SyncState
import com.mowtiie.writa.feature.notes.cache.CacheOwnerSource
import com.mowtiie.writa.feature.notes.cache.ClientIdGenerator
import com.mowtiie.writa.feature.notes.cache.NoteCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
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
import java.util.concurrent.TimeUnit

class SyncEngineTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var fixture: NotesFixture
    private val server get() = fixture.server
    private val dao get() = fixture.dao
    private val crypto get() = fixture.crypto
    private val codec get() = fixture.codec
    private val owner get() = fixture.owner
    private val cache get() = fixture.cache
    private val engine get() = fixture.engine

    @Before
    fun setUp() {
        fixture = NotesFixture(folder.root).also { it.start() }
    }

    @After
    fun tearDown() = fixture.close()

    // ---------------------------- helpers ----------------------------

    private fun row(
        id: Long,
        title: String = "t$id",
        content: String = "c$id",
        updatedAt: String = T1,
    ) = """{"id":$id,"title":"$title","content":"$content","tags":[],"pinned":false,"archived":false,""" +
        """"created_at":"$T0","updated_at":"$updatedAt","deleted_at":null,"client_id":null}"""

    private fun ok(vararg rows: String) =
        MockResponse().setResponseCode(200).setBody(rows.joinToString(prefix = "[", postfix = "]"))

    private fun note(
        id: Long,
        title: String = "t$id",
        content: String? = "c$id",
        updatedAt: String? = T1,
        tags: List<String> = emptyList(),
    ) = Note(id, title, content, tags, false, false, T0, updatedAt, null)

    private fun seedSynced(
        id: Long,
        updatedAt: String = T1,
    ) = runBlocking { dao.insert(codec.encode(note(id, updatedAt = updatedAt), id)) }

    private fun raw(id: Long) = runBlocking { dao.get(id) }

    private fun pending() = runBlocking { cache.pending() }

    private fun requestBody(request: RecordedRequest): JsonObject =
        Json.parseToJsonElement(request.body.readUtf8()).jsonObject

    private fun next(): RecordedRequest =
        server.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS) ?: error("no request arrived")

    private fun createOffline(
        title: String = "Idea",
        content: String? = "body",
        tags: List<String> = emptyList(),
        pinned: Boolean = false,
    ) = runBlocking { cache.createLocal(title, content, tags, pinned) }

    private fun edit(
        note: Note,
        mask: Int,
    ) = runBlocking { cache.saveLocal(note, mask) }

    private fun pass() = runBlocking { engine.runPass() }

    private fun stateOf(id: Long) = runBlocking { cache.get(id)?.syncState }

    private fun failedNotes() = dao.rows.filter { it.syncFailed }

    // ---------------------------- creating ----------------------------

    @Test
    fun aNoteMadeOfflineIsCreatedWithItsClientIdAndAdoptsTheServersId() {
        val local = createOffline("Idea", "body", listOf("work"), pinned = true)
        val clientId = raw(local.id)!!.clientId
        server.enqueue(ok(row(55, title = "Idea", content = "body")))

        assertTrue(pass())

        val request = next()
        assertEquals("POST", request.method)
        val sent = requestBody(request)
        assertEquals(setOf("title", "content", "tags", "pinned", "archived", "client_id"), sent.keys)
        assertEquals("Idea", sent.getValue("title").jsonPrimitive.content)
        assertEquals(
            "work",
            sent
                .getValue("tags")
                .jsonArray
                .single()
                .jsonPrimitive.content,
        )
        assertTrue(sent.getValue("pinned").jsonPrimitive.boolean)
        assertEquals(clientId, sent.getValue("client_id").jsonPrimitive.content)
        assertNull(raw(local.id))
        assertEquals(SyncState.Synced, stateOf(55))
        assertEquals(55L, dao.aliases[local.id])
        assertTrue(pending().isEmpty())
    }

    @Test
    fun aNoteTrashedBeforeItWasUploadedIsCreatedInTheTrash() {
        val local = createOffline()
        edit(local.copy(deletedAt = "2026-11-02T00:00:00Z"), DirtyMask.TRASH)
        server.enqueue(ok(row(55)))

        assertTrue(pass())

        assertEquals("2026-11-02T00:00:00Z", requestBody(next()).getValue("deleted_at").jsonPrimitive.content)
    }

    @Test
    fun aRetriedUploadAdoptsTheNoteTheLostAttemptMade() {
        val local = createOffline()
        val clientId = raw(local.id)!!.clientId
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"code":"23505","message":"duplicate key"}"""))
        server.enqueue(ok(row(70)))

        assertTrue(pass())

        assertEquals("POST", next().method)
        val lookup = next()
        assertEquals("GET", lookup.method)
        assertEquals("eq.$clientId", lookup.requestUrl?.queryParameter("client_id"))
        assertEquals(SyncState.Synced, stateOf(70))
        assertNull(raw(local.id))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun aConflictWhoseNoteCannotBeFoundMarksTheNoteFailed() {
        val local = createOffline()
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"code":"23505"}"""))
        server.enqueue(ok())

        assertTrue(pass())

        assertEquals(SyncState.Failed, stateOf(local.id))
    }

    @Test
    fun newNotesAreUploadedBeforeChangesToOldOnes() {
        seedSynced(5)
        edit(note(5), DirtyMask.PINNED)
        createOffline()
        server.enqueue(ok(row(60)))
        server.enqueue(ok(row(5)))

        assertTrue(pass())

        assertEquals("POST", next().method)
        assertEquals("PATCH", next().method)
    }

    // ---------------------------- updating ----------------------------

    @Test
    fun aTextEditIsSentOnlyIfTheServersTextIsStillTheOneItWasBasedOn() {
        seedSynced(5, updatedAt = T1)
        edit(note(5, title = "New", content = "new"), DirtyMask.TEXT)
        server.enqueue(ok(row(5, title = "New", content = "new", updatedAt = T2)))

        assertTrue(pass())

        val request = next()
        assertEquals("PATCH", request.method)
        assertEquals(listOf("eq.$T1"), request.requestUrl?.queryParameterValues("updated_at"))
        assertEquals("eq.5", request.requestUrl?.queryParameter("id"))
        assertEquals(setOf("title", "content"), requestBody(request).keys)
        assertEquals(T2, runBlocking { cache.get(5) }?.updatedAt)
        assertEquals(SyncState.Synced, stateOf(5))
    }

    @Test
    fun aChangeThatLeavesTheTextAloneIsNotCheckedAgainstTheServersText() {
        seedSynced(5)
        edit(note(5).copy(pinned = true), DirtyMask.PINNED)
        server.enqueue(ok(row(5)))

        assertTrue(pass())

        val request = next()
        assertNull(request.requestUrl?.queryParameter("updated_at"))
        val sent = requestBody(request)
        assertEquals(setOf("pinned"), sent.keys)
        assertTrue(sent.getValue("pinned").jsonPrimitive.boolean)
    }

    @Test
    fun tagsAndArchiveAreSentAsTheyAre() {
        seedSynced(5)
        edit(note(5, tags = listOf("a", "b")).copy(archived = true), DirtyMask.TAGS or DirtyMask.ARCHIVED)
        server.enqueue(ok(row(5)))

        assertTrue(pass())

        val sent = requestBody(next())
        assertEquals(setOf("tags", "archived"), sent.keys)
        assertEquals(listOf("a", "b"), sent.getValue("tags").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun trashingAndRestoringSendDeletedAt() {
        seedSynced(5)
        seedSynced(6)
        edit(note(5).copy(deletedAt = "2026-11-02T00:00:00Z"), DirtyMask.TRASH)
        edit(note(6).copy(deletedAt = null), DirtyMask.TRASH)
        server.enqueue(ok(row(5)))
        server.enqueue(ok(row(6)))

        assertTrue(pass())

        assertEquals("2026-11-02T00:00:00Z", requestBody(next()).getValue("deleted_at").jsonPrimitive.content)
        assertEquals(JsonNull, requestBody(next()).getValue("deleted_at"))
    }

    // ---------------------------- conflicts ----------------------------

    @Test
    fun aTextConflictKeepsBothVersions() {
        seedSynced(5, updatedAt = T1)
        edit(note(5, title = "Title", content = "my edit", tags = listOf("a")), DirtyMask.TEXT)
        server.enqueue(ok())
        server.enqueue(ok(row(5, title = "Title", content = "their edit", updatedAt = T3)))
        server.enqueue(ok(row(100, title = "Title (conflict)", content = "my edit")))

        assertTrue(pass())

        assertEquals("PATCH", next().method)
        val fetch = next()
        assertEquals("GET", fetch.method)
        assertEquals("eq.5", fetch.requestUrl?.queryParameter("id"))
        val copy = next()
        assertEquals("POST", copy.method)
        val sent = requestBody(copy)
        assertEquals("Title (conflict)", sent.getValue("title").jsonPrimitive.content)
        assertEquals("my edit", sent.getValue("content").jsonPrimitive.content)
        assertEquals(listOf("a"), sent.getValue("tags").jsonArray.map { it.jsonPrimitive.content })
        assertTrue(
            sent
                .getValue("client_id")
                .jsonPrimitive.content
                .startsWith("engine-"),
        )
        assertFalse(sent.getValue("pinned").jsonPrimitive.boolean)
        val original = runBlocking { cache.get(5) }
        assertEquals("their edit", original?.content)
        assertEquals(SyncState.Synced, original?.syncState)
        assertTrue(pending().isEmpty())
        val storedCopy = runBlocking { cache.get(100) }
        assertEquals("Title (conflict)", storedCopy?.title)
        assertEquals(SyncState.Synced, storedCopy?.syncState)
    }

    @Test
    fun aFailedConflictCopyLeavesTheEditQueued() {
        seedSynced(5)
        edit(note(5, content = "my edit"), DirtyMask.TEXT)
        server.enqueue(ok())
        server.enqueue(ok(row(5, content = "their edit", updatedAt = T3)))
        server.enqueue(MockResponse().setResponseCode(503))

        assertFalse(pass())

        assertEquals("my edit", runBlocking { cache.get(5) }?.content)
        assertEquals(1, pending().size)
        assertTrue(failedNotes().isEmpty())
    }

    @Test
    fun anEditToANoteDeletedElsewhereIsKeptAsANewNote() {
        seedSynced(5)
        edit(note(5, title = "Title", content = "my edit"), DirtyMask.TEXT)
        server.enqueue(ok())
        server.enqueue(ok())
        server.enqueue(ok(row(100, title = "Title", content = "my edit")))

        assertTrue(pass())

        assertEquals("PATCH", next().method)
        assertEquals("GET", next().method)
        val created = requestBody(next())
        assertEquals("Title", created.getValue("title").jsonPrimitive.content)
        assertEquals("my edit", created.getValue("content").jsonPrimitive.content)
        assertNull(raw(5))
        assertEquals("my edit", runBlocking { cache.get(100) }?.content)
        assertEquals(100L, dao.aliases[5])
    }

    @Test
    fun aFlagChangeToANoteDeletedElsewhereIsDropped() {
        seedSynced(5)
        edit(note(5).copy(pinned = true), DirtyMask.PINNED)
        server.enqueue(ok())

        assertTrue(pass())

        assertNull(raw(5))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun aChangeMadeWhileTheNoteWasUploadingIsKeptAndSentNext() {
        seedSynced(5)
        edit(note(5, title = "first"), DirtyMask.TEXT)
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    edit(note(5, title = "second"), DirtyMask.TEXT)
                    return ok(row(5, title = "first", updatedAt = T2))
                }
            }

        assertTrue(pass())

        val stored = raw(5)!!
        assertEquals("second", codec.decode(stored)?.title)
        assertEquals(DirtyMask.TEXT, stored.dirtyMask)
        assertEquals(T2, stored.baseUpdatedAt)
        assertEquals(1, pending().size)
    }

    // ---------------------------- deleting ----------------------------

    @Test
    fun aDeleteForGoodIsUploadedAndForgotten() {
        seedSynced(5)
        runBlocking { cache.deleteLocal(5) }
        server.enqueue(MockResponse().setResponseCode(204))

        assertTrue(pass())

        val request = next()
        assertEquals("DELETE", request.method)
        assertEquals("eq.5", request.requestUrl?.queryParameter("id"))
        assertNull(raw(5))
        assertTrue(pending().isEmpty())
    }

    @Test
    fun deletingANoteTheServerNeverSawMakesNoRequest() {
        val local = createOffline()
        runBlocking { cache.deleteLocal(local.id) }

        assertTrue(pass())

        assertEquals(0, server.requestCount)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun aRefusedDeleteMarksTheNoteFailed() {
        seedSynced(5)
        runBlocking { cache.deleteLocal(5) }
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"message":"denied"}"""))

        assertTrue(pass())

        assertTrue(raw(5)!!.syncFailed)
        assertTrue(pending().isEmpty())
    }

    // ---------------------------- errors ----------------------------

    @Test
    fun aServerErrorStopsThePassAndKeepsEverythingQueued() {
        createOffline("one")
        createOffline("two")
        server.enqueue(MockResponse().setResponseCode(500))

        assertFalse(pass())

        assertEquals(1, server.requestCount)
        assertEquals(2, pending().size)
        assertTrue(failedNotes().isEmpty())
    }

    @Test
    fun trafficJamAnswersAreTemporary() {
        for (status in listOf(408, 429, 503)) {
            val local = createOffline("n$status")
            server.enqueue(MockResponse().setResponseCode(status))

            assertFalse(pass())

            assertTrue(failedNotes().isEmpty())
            runBlocking { cache.deleteLocal(local.id) }
        }
    }

    @Test
    fun anUnreachableServerLosesNothing() {
        createOffline()
        server.shutdown()

        assertFalse(pass())

        assertEquals(1, pending().size)
        assertTrue(failedNotes().isEmpty())
    }

    @Test
    fun anExpiredSessionStopsThePass() {
        createOffline("one")
        createOffline("two")
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))

        assertFalse(pass())

        assertEquals(2, server.requestCount)
        assertEquals(2, pending().size)
        assertTrue(failedNotes().isEmpty())
    }

    @Test
    fun aRefusedChangeIsMarkedFailedAndTheRestStillGoOut() {
        createOffline("one")
        createOffline("two")
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"message":"too long"}"""))
        server.enqueue(ok(row(12, title = "two")))

        assertTrue(pass())

        assertEquals(2, server.requestCount)
        assertNotNull(raw(12))
        val failed = failedNotes().single()
        assertEquals(SyncState.Failed, stateOf(failed.id))
        assertTrue(pending().isEmpty())

        runBlocking { cache.retryFailed(failed.id) }
        assertEquals(1, pending().size)
    }

    @Test
    fun aPageOfHtmlInsteadOfJsonIsTemporary() {
        createOffline()
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>Wi-Fi login</html>"))

        assertFalse(pass())

        assertEquals(1, pending().size)
        assertTrue(failedNotes().isEmpty())
    }

    @Test
    fun anOutdatedDatabaseStopsThePassWithoutFailingTheNote() {
        createOffline()
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":"PGRST204","message":"no client_id"}"""))

        assertFalse(pass())

        assertEquals(1, pending().size)
        assertTrue(failedNotes().isEmpty())
    }

    @Test
    fun aSignedOutCacheHasNothingToUpload() {
        createOffline()
        owner.value = null

        assertTrue(pass())

        assertEquals(0, server.requestCount)
    }

    // ---------------------------- the worker ----------------------------

    @Test
    fun aTriggerUploadsInTheBackground() {
        val local = createOffline()
        server.enqueue(ok(row(55)))

        engine.trigger()

        runBlocking { withTimeout(WAIT_MS) { while (raw(local.id) != null) delay(POLL_MS) } }
        assertEquals(SyncState.Synced, stateOf(55))
    }

    @Test
    fun syncReturnsOnceTheUploadHasBeenTried() {
        val local = createOffline()
        server.enqueue(ok(row(55)))

        runBlocking { withTimeout(WAIT_MS) { engine.sync() } }

        assertNull(raw(local.id))
        assertNotNull(raw(55))
    }

    @Test
    fun requestsMadeWhileAPassRunsShareOneUpload() {
        createOffline()
        server.enqueue(ok(row(55)).setHeadersDelay(SLOW_MS, TimeUnit.MILLISECONDS))

        runBlocking { withTimeout(WAIT_MS) { List(3) { async { engine.sync() } }.awaitAll() } }

        assertEquals(1, server.requestCount)
        assertTrue(pending().isEmpty())
    }

    @Test
    fun afterAFailedPassTheRequestsThatArrivedMeanwhileDoNotHammerTheServer() {
        createOffline()
        server.enqueue(MockResponse().setResponseCode(500).setHeadersDelay(SLOW_MS, TimeUnit.MILLISECONDS))

        runBlocking {
            withTimeout(WAIT_MS) {
                val first = async { engine.sync() }
                while (server.requestCount == 0) delay(POLL_MS)
                val second = async { engine.sync() }
                listOf(first, second).awaitAll()
                delay(SLOW_MS) // long enough for a wrongly started second pass to reach the server
            }
        }

        assertEquals(1, server.requestCount)
        assertEquals(1, pending().size)
    }

    @Test
    fun aPassWaitsWhileARefreshHoldsPassesOff() {
        createOffline()
        server.enqueue(ok(row(55)))
        val release = CompletableDeferred<Unit>()

        runBlocking {
            val holder = launch(Dispatchers.Default) { engine.withPassesPaused { release.await() } }
            delay(SLOW_MS)
            val pass = async(Dispatchers.Default) { engine.runPass() }
            delay(SLOW_MS)
            assertEquals(0, server.requestCount)
            assertFalse(pass.isCompleted)

            release.complete(Unit)
            withTimeout(WAIT_MS) { assertTrue(pass.await()) }
            holder.join()
        }

        assertEquals(1, server.requestCount)
    }

    @Test
    fun aPassThatCrashesDoesNotStopTheWorker() {
        val local = createOffline()
        server.enqueue(ok(row(55)))
        crypto.failToEncrypt = true

        runBlocking { withTimeout(WAIT_MS) { engine.sync() } }

        assertNotNull(raw(local.id))
        crypto.failToEncrypt = false
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"code":"23505"}"""))
        server.enqueue(ok(row(55)))

        runBlocking { withTimeout(WAIT_MS) { engine.sync() } }

        assertNull(raw(local.id))
        assertNotNull(raw(55))
    }

    private companion object {
        const val T0 = "2026-11-01T10:00:00+00:00"
        const val T1 = "2026-11-01T11:00:00.123456+00:00"
        const val T2 = "2026-11-01T12:00:00+00:00"
        const val T3 = "2026-11-01T13:00:00+00:00"
        const val WAIT_SECONDS = 5L
        const val WAIT_MS = 10_000L
        const val POLL_MS = 20L
        const val SLOW_MS = 300L
    }
}
