package com.mowtiie.writa.feature.settings.backup

import com.mowtiie.writa.core.network.ApiException
import com.mowtiie.writa.feature.notes.Note
import com.mowtiie.writa.feature.notes.NoteRepository
import com.mowtiie.writa.feature.notes.NotesFixture
import com.mowtiie.writa.feature.notes.remote.PartialImportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

private class FakeBackupCodec : BackupCodec {
    var writtenTo: OutputStream? = null
    var written: List<BackupNote>? = null
    var exportedAt: Instant? = null
    var toRead: List<BackupNote> = emptyList()
    var readError: IOException? = null
    var readFrom: InputStream? = null

    override fun write(
        out: OutputStream,
        notes: List<BackupNote>,
        exportedAt: Instant,
    ) {
        writtenTo = out
        written = notes
        this.exportedAt = exportedAt
    }

    override fun read(input: InputStream): List<BackupNote> {
        readFrom = input
        readError?.let { throw it }
        return toRead
    }
}

class BackupServiceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val clock = Clock.fixed(Instant.parse("2026-11-05T12:30:09Z"), ZoneOffset.UTC)
    private val codec = FakeBackupCodec()
    private lateinit var fixture: NotesFixture
    private lateinit var notes: NoteRepository
    private lateinit var service: BackupService

    private val server get() = fixture.server

    @Before
    fun setUp() {
        fixture = NotesFixture(folder.root, clock).also { it.start() }
        (server.dispatcher as QueueDispatcher).setFailFast(MockResponse().setResponseCode(503))
        notes = NoteRepository(fixture.cache, fixture.backend.remote, fixture.engine, clock)
        service = BackupService(notes, fixture.backend.remote, codec, clock, Dispatchers.IO)
    }

    @After
    fun tearDown() = fixture.close()

    private fun row(id: Long) =
        """{"id":$id,"title":"imported $id","content":"c","tags":[],"pinned":false,"archived":false,""" +
            """"created_at":"2026-11-01T10:00:00+00:00","updated_at":"2026-11-01T10:00:00+00:00",""" +
            """"deleted_at":null,"client_id":null}"""

    private fun seed(
        id: Long,
        title: String = "t$id",
        archived: Boolean = false,
        deletedAt: String? = null,
    ) = runBlocking {
        val note = Note(id, title, "c$id", listOf("x"), false, archived, "2026-01-01T00:00:00Z", null, deletedAt)
        fixture.dao.insert(fixture.codec.encode(note, id))
    }

    private fun backupNote(
        title: String,
        pinned: Boolean = false,
    ) = BackupNote(title, "body", "2020-01-01T00:00:00Z", listOf("a"), pinned, archived = false)

    private fun import(list: List<BackupNote>) = runBlocking { service.import(list) }

    private fun methods(): List<String> =
        generateSequence {
            server.takeRequest(
                SHORT_WAIT_MS,
                TimeUnit.MILLISECONDS,
            )
        }.map { it.method.orEmpty() }.toList()

    private fun body(request: RecordedRequest) = Json.parseToJsonElement(request.body.readUtf8())

    // ---------------------------- export ----------------------------

    @Test
    fun exportWritesTheNotesOnThisDeviceExceptTrashedOnes() {
        seed(1)
        seed(2, archived = true)
        seed(3, deletedAt = "2026-02-01T00:00:00Z")
        runBlocking { notes.setPinned(1, true) } // an unsent change is exported too
        val out = ByteArrayOutputStream()

        runBlocking { service.export(out) }

        val written = codec.written.orEmpty()
        assertEquals(setOf("t1", "t2"), written.map { it.title }.toSet())
        assertTrue(written.single { it.title == "t1" }.pinned)
        assertTrue(written.single { it.title == "t2" }.archived)
        assertSame(out, codec.writtenTo)
        assertEquals(Instant.parse("2026-11-05T12:30:09Z"), codec.exportedAt)
    }

    @Test
    fun exportingNothingWritesAnEmptyBackup() {
        runBlocking { service.export(ByteArrayOutputStream()) }

        assertEquals(emptyList<BackupNote>(), codec.written)
    }

    @Test
    fun exportDoesNotNeedTheNetwork() {
        seed(1)
        server.shutdown()

        runBlocking { service.export(ByteArrayOutputStream()) }

        assertEquals(1, codec.written?.size)
    }

    // ---------------------------- reading ----------------------------

    @Test
    fun readGivesTheNotesOfTheFile() {
        codec.toRead = listOf(backupNote("one"), backupNote("two"))
        val input = ByteArrayInputStream(ByteArray(0))

        val read = runBlocking { service.read(input) }

        assertEquals(listOf("one", "two"), read.map { it.title })
        assertSame(input, codec.readFrom)
    }

    @Test
    fun anInvalidFileIsReportedNotSwallowed() {
        codec.readError = InvalidBackupException("not a backup")

        assertThrows(
            InvalidBackupException::class.java,
        ) { runBlocking { service.read(ByteArrayInputStream(ByteArray(0))) } }
    }

    @Test
    fun aNewerFileIsReportedWithItsVersion() {
        codec.readError = UnsupportedBackupVersionException(3)

        val error =
            assertThrows(UnsupportedBackupVersionException::class.java) {
                runBlocking { service.read(ByteArrayInputStream(ByteArray(0))) }
            }

        assertEquals(3, error.version)
    }

    // ---------------------------- import ----------------------------

    @Test
    fun importInsertsNewNotesWithoutTheCreationTimeThenRefreshes() {
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setBody("[${row(81)}]"))

        val imported = import(listOf(backupNote("Imported", pinned = true)))

        assertEquals(1, imported)
        val insert = server.takeRequest()
        assertEquals("POST", insert.method)
        assertEquals("return=minimal", insert.getHeader("Prefer"))
        val sent = body(insert).jsonArray.single().jsonObject
        assertEquals("Imported", sent.getValue("title").jsonPrimitive.content)
        assertFalse("created_at" in sent || "createdAt" in sent)
        assertEquals("GET", server.takeRequest().method)
        assertEquals(listOf("imported 81"), runBlocking { notes.observeActive().first() }.map { it.title })
    }

    @Test
    fun importingNothingDoesNothing() {
        assertEquals(0, import(emptyList()))

        assertEquals(0, server.requestCount)
    }

    @Test
    fun importGoesOutInBatchesOf200ThenRefreshes() {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(201)) }
        server.enqueue(MockResponse().setBody("[]"))

        assertEquals(450, import(List(450) { backupNote("n$it") }))

        val sizes = List(3) { body(server.takeRequest()).jsonArray.size }
        assertEquals(listOf(200, 200, 50), sizes)
        assertEquals("GET", server.takeRequest().method)
    }

    @Test
    fun aFailureAfterSomeBatchesIsPartialAndStillRefreshesSoTheNewNotesShow() {
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"message":"boom"}"""))
        server.enqueue(MockResponse().setBody("[${row(81)}]"))

        val error = assertThrows(PartialImportException::class.java) { import(List(450) { backupNote("n$it") }) }

        assertEquals(200, error.imported)
        assertEquals(450, error.total)
        assertEquals(listOf("POST", "POST", "GET"), methods())
    }

    @Test
    fun aFailureOfTheFirstBatchIsPlainAndDoesNotRefresh() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"message":"boom"}"""))

        val error = assertThrows(ApiException::class.java) { import(listOf(backupNote("a"))) }

        assertEquals(500, error.code)
        assertEquals(listOf("POST"), methods())
    }

    @Test
    fun anUnreachableServerFailsTheImportWithoutTouchingTheDevice() {
        seed(1)
        server.shutdown()

        assertThrows(IOException::class.java) { import(listOf(backupNote("a"))) }

        assertEquals(1, fixture.dao.rows.size)
    }

    @Test
    fun signedOutThereIsNoSessionToImportWith() {
        runBlocking { fixture.backend.sessionStore.clear() }

        assertThrows(IOException::class.java) { import(listOf(backupNote("a"))) }

        assertEquals(0, server.requestCount)
    }

    // ---------------------------- file name ----------------------------

    @Test
    fun theSuggestedFileNameCarriesTheUtcTime() {
        assertEquals("writa_backup_20261105_123009.json", service.suggestedFileName())
    }

    private companion object {
        const val SHORT_WAIT_MS = 300L
    }
}
