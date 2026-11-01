package com.mowtiie.writa.feature.notes.remote

import com.mowtiie.writa.core.datastore.ConnectionStore
import com.mowtiie.writa.core.network.ApiException
import com.mowtiie.writa.core.network.ConnectionNotConfiguredException
import com.mowtiie.writa.core.network.HttpClients
import com.mowtiie.writa.core.network.NetworkErrors
import com.mowtiie.writa.core.network.NotSignedInException
import com.mowtiie.writa.core.network.SchemaOutdatedException
import com.mowtiie.writa.core.network.TestBackend
import com.mowtiie.writa.core.network.TokenAuthenticator
import com.mowtiie.writa.core.network.apiJson
import com.mowtiie.writa.core.security.FakeSecureStorage
import com.mowtiie.writa.core.security.FakeSecureStorageFactory
import com.mowtiie.writa.core.security.Session
import com.mowtiie.writa.core.security.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class NoteRemoteDataSourceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var backend: TestBackend
    private val server get() = backend.server
    private val connectionStore get() = backend.connectionStore
    private val sessionStore get() = backend.sessionStore
    private val dataSource get() = backend.remote

    @Before
    fun setUp() {
        backend = TestBackend(folder.root).also { it.start() }
    }

    @After
    fun tearDown() = backend.close()

    private fun row(
        id: Long,
        extra: String = "",
        title: String = "t$id",
    ) = """{"id":$id,"title":"$title","content":"c","tags":[],"pinned":false,"archived":false,""" +
        """"created_at":"2026-01-01T00:00:00+00:00","updated_at":null,"deleted_at":null,"client_id":null$extra}"""

    private fun rows(vararg ids: Long) = ids.joinToString(prefix = "[", postfix = "]") { row(it) }

    private fun fullPage(start: Long) = rows(*LongArray(NoteRemoteDataSource.PAGE_SIZE) { start + it })

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    private fun body(request: RecordedRequest): JsonObject = Json.parseToJsonElement(request.body.readUtf8()).jsonObject

    private fun bodyArray(request: RecordedRequest): JsonArray =
        Json.parseToJsonElement(request.body.readUtf8()).jsonArray

    private suspend fun failureOf(block: suspend () -> Unit): Throwable? = runCatching { block() }.exceptionOrNull()

    @Test
    fun fetchPageAsksForNewestFirstWithTheUsersCredentials() =
        runTest {
            server.enqueue(ok("[]"))

            dataSource.fetchPage(offset = 400, limit = 50)

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/rest/v1/notes", request.requestUrl?.encodedPath)
            assertEquals("*", request.requestUrl?.queryParameter("select"))
            assertEquals("created_at.desc,id.desc", request.requestUrl?.queryParameter("order"))
            assertEquals("50", request.requestUrl?.queryParameter("limit"))
            assertEquals("400", request.requestUrl?.queryParameter("offset"))
            assertEquals(ANON_KEY, request.getHeader("apikey"))
            assertEquals("Bearer user-token", request.getHeader("Authorization"))
        }

    @Test
    fun fetchAllReadsEveryPage() =
        runTest {
            val pageSize = NoteRemoteDataSource.PAGE_SIZE
            server.enqueue(ok(fullPage(0)))
            server.enqueue(ok(fullPage(1000)))
            server.enqueue(ok(rows(5000, 5001, 5002, 5003, 5004)))

            val notes = dataSource.fetchAll()

            assertEquals(pageSize * 2 + 5, notes.size)
            assertEquals(
                listOf("0", "200", "400"),
                List(3) { server.takeRequest().requestUrl?.queryParameter("offset") },
            )
        }

    @Test
    fun fetchAllStopsAfterAnEmptyPageWhenTheLastPageWasFull() =
        runTest {
            server.enqueue(
                ok(fullPage(0)),
            )
            server.enqueue(ok("[]"))

            assertEquals(NoteRemoteDataSource.PAGE_SIZE, dataSource.fetchAll().size)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun fetchAllFailsWhenALaterPageFails() =
        runTest {
            server.enqueue(
                ok(fullPage(0)),
            )
            server.enqueue(MockResponse().setResponseCode(500))

            val error = failureOf { dataSource.fetchAll() }

            assertEquals(500, (error as ApiException).code)
        }

    @Test
    fun parsesNullsMissingKeysUnknownKeysAndLargeIds() =
        runTest {
            val big = 5_000_000_000L
            val text =
                """[{"id":$big,"title":"T","content":null,"tags":null,"pinned":null,"archived":null,""" +
                    """"created_at":null,"updated_at":null,"deleted_at":"2026-02-02T00:00:00+00:00",""" +
                    """"client_id":"abc","future_column":{"a":1}}]"""
            server.enqueue(ok(text))

            val note = dataSource.fetchPage(0).single()

            assertEquals(
                RemoteNote(big, "T", null, null, null, null, null, null, "2026-02-02T00:00:00+00:00", "abc"),
                note,
            )
        }

    @Test
    fun keepsTrashedAndArchivedRows() =
        runTest {
            server.enqueue(
                ok("[" + row(1, title = "a").replace("\"archived\":false", "\"archived\":true") + "," + row(2) + "]"),
            )

            val notes = dataSource.fetchPage(0)

            assertEquals(listOf(true, false), notes.map { it.archived })
        }

    @Test
    fun aRowWithoutAColumnMeansTheSchemaIsOutdated() =
        runTest {
            server.enqueue(ok("""[{"id":1,"title":"t","content":"c","tags":[],"pinned":false,"created_at":null}]"""))

            val error = failureOf { dataSource.fetchPage(0) } as SchemaOutdatedException

            assertEquals(listOf("archived", "updated_at", "deleted_at", "client_id"), error.missingColumns)
        }

    @Test
    fun anEmptyResultIsNotASchemaProblem() =
        runTest {
            server.enqueue(ok("[]"))

            assertTrue(dataSource.fetchPage(0).isEmpty())
        }

    @Test
    fun postgrestSchemaErrorsMeanTheSchemaIsOutdated() =
        runTest {
            for (code in listOf("42703", "42P01", "PGRST204", "PGRST205")) {
                server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":"$code","message":"x"}"""))

                assertTrue(failureOf { dataSource.fetchPage(0) } is SchemaOutdatedException)
            }
        }

    @Test
    fun otherClientErrorsStayApiExceptionsWithoutRowDetails() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(400).setBody(
                    """{"code":"23502","message":"null value in column","details":"Failing row contains (my private note)"}""",
                ),
            )

            val error = failureOf { dataSource.create(NoteInsert("t", "c")) } as ApiException

            assertEquals(400, error.code)
            assertFalse(error.message.orEmpty().contains("private"))
        }

    @Test
    fun fetchByIdFiltersOnTheLongId() =
        runTest {
            val id = 5_000_000_000L
            server.enqueue(ok(rows(id)))

            assertEquals(id, dataSource.fetch(id)?.id)
            assertEquals("eq.$id", server.takeRequest().requestUrl?.queryParameter("id"))
        }

    @Test
    fun fetchByIdIsNullWhenTheNoteIsGone() =
        runTest {
            server.enqueue(ok("[]"))

            assertNull(dataSource.fetch(7))
        }

    @Test
    fun findByClientIdFiltersOnTheClientId() =
        runTest {
            server.enqueue(ok(rows(3)))

            assertEquals(3L, dataSource.findByClientId("uuid-1")?.id)
            assertEquals("eq.uuid-1", server.takeRequest().requestUrl?.queryParameter("client_id"))
        }

    @Test
    fun createCanPutTheNoteStraightIntoTheTrash() =
        runTest {
            server.enqueue(ok(rows(9)))

            dataSource.create(NoteInsert("t", "c", deletedAt = "2026-11-02T00:00:00Z"))

            val sent = body(server.takeRequest())
            assertEquals("2026-11-02T00:00:00Z", sent.getValue("deleted_at").jsonPrimitive.content)
        }

    @Test
    fun bulkInsertNeverSendsDeletedAt() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(201))

            dataSource.insertAll(listOf(NoteInsert("t", "c", deletedAt = "2026-11-02T00:00:00Z")))

            assertEquals(setOf("title", "content"), bodyArray(server.takeRequest()).single().jsonObject.keys)
        }

    @Test
    fun createPostsTheWholeNoteWithItsClientIdAndNeverAUserId() =
        runTest {
            server.enqueue(ok(rows(9)))

            val created =
                dataSource.create(
                    NoteInsert("Title", "Body", listOf("a", "b"), pinned = true, archived = false, clientId = "uuid-1"),
                )

            assertEquals(9L, created.id)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("return=representation", request.getHeader("Prefer"))
            assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
            val sent = body(request)
            assertEquals(setOf("title", "content", "tags", "pinned", "archived", "client_id"), sent.keys)
            assertEquals("Title", sent.getValue("title").jsonPrimitive.content)
            assertEquals(listOf("a", "b"), sent.getValue("tags").jsonArray.map { it.jsonPrimitive.content })
            assertTrue(sent.getValue("pinned").jsonPrimitive.boolean)
            assertEquals("uuid-1", sent.getValue("client_id").jsonPrimitive.content)
        }

    @Test
    fun createReportsADuplicateClientIdAs409() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(409).setBody("""{"code":"23505","message":"duplicate key"}"""),
            )

            val error = failureOf { dataSource.create(NoteInsert("t", "c", clientId = "uuid-1")) } as ApiException

            assertEquals(409, error.code)
        }

    @Test
    fun createWithoutAnAnswerRowIsAnError() =
        runTest {
            server.enqueue(ok("[]"))

            assertTrue(failureOf { dataSource.create(NoteInsert("t", "c")) } is IOException)
        }

    @Test
    fun updateSendsOnlyTheChangedColumns() =
        runTest {
            server.enqueue(ok(rows(5)))

            dataSource.update(5, NotePatch(title = "New", pinned = true))

            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("eq.5", request.requestUrl?.queryParameter("id"))
            assertNull(request.requestUrl?.queryParameter("updated_at"))
            assertEquals("return=representation", request.getHeader("Prefer"))
            assertEquals(setOf("title", "pinned"), body(request).keys)
        }

    @Test
    fun updateIsConditionalOnTheBaseTimestampAndKeepsThePlusSign() =
        runTest {
            val base = "2026-11-01T10:00:00.123456+00:00"
            server.enqueue(ok(rows(5)))

            dataSource.update(5, NotePatch(content = "x"), ifUpdatedAt = base)

            val request = server.takeRequest()
            assertEquals("eq.$base", request.requestUrl?.queryParameter("updated_at"))
            assertTrue(request.path.orEmpty().contains("%2B"))
        }

    @Test
    fun updateIsNullWhenNothingMatched() =
        runTest {
            server.enqueue(ok("[]"))

            assertNull(dataSource.update(5, NotePatch(title = "x"), ifUpdatedAt = "2026-01-01T00:00:00+00:00"))
        }

    @Test
    fun trashingSetsDeletedAtAndRestoringSendsNull() =
        runTest {
            server.enqueue(ok(rows(5)))
            server.enqueue(ok(rows(5)))

            dataSource.update(5, NotePatch(deletedAt = DeletedAtChange.MoveToTrash("2026-11-01T10:00:00Z")))
            dataSource.update(5, NotePatch(deletedAt = DeletedAtChange.Restore))

            assertEquals(JsonPrimitive("2026-11-01T10:00:00Z"), body(server.takeRequest()).getValue("deleted_at"))
            assertEquals(JsonNull, body(server.takeRequest()).getValue("deleted_at"))
        }

    @Test
    fun anEmptyPatchSendsAnEmptyObjectAndTagsGoAsAnArray() =
        runTest {
            server.enqueue(ok(rows(5)))
            server.enqueue(ok(rows(5)))

            dataSource.update(5, NotePatch())
            dataSource.update(5, NotePatch(tags = listOf("x"), archived = true))

            assertTrue(body(server.takeRequest()).isEmpty())
            val second = body(server.takeRequest())
            assertEquals(listOf("x"), second.getValue("tags").jsonArray.map { it.jsonPrimitive.content })
            assertTrue(second.getValue("archived").jsonPrimitive.boolean)
        }

    @Test
    fun deleteRemovesTheNoteById() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(204))

            dataSource.delete(5_000_000_000L)

            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("eq.5000000000", request.requestUrl?.queryParameter("id"))
        }

    @Test
    fun deleteFailuresAreApiExceptions() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"message":"denied"}"""))

            assertEquals(403, (failureOf { dataSource.delete(1) } as ApiException).code)
        }

    @Test
    fun insertAllSendsBatchesOf200AndReturnsTheCount() =
        runTest {
            repeat(3) { server.enqueue(MockResponse().setResponseCode(201)) }

            val imported = dataSource.insertAll(List(450) { NoteInsert("n$it", "c") })

            assertEquals(450, imported)
            val sizes = List(3) { bodyArray(server.takeRequest()).size }
            assertEquals(listOf(200, 200, 50), sizes)
        }

    @Test
    fun insertAllAsksForAMinimalAnswerAndNeverSendsUserOrClientIds() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(201))

            dataSource.insertAll(listOf(NoteInsert("n", "c", clientId = "ignored")))

            val request = server.takeRequest()
            assertEquals("return=minimal", request.getHeader("Prefer"))
            assertEquals(setOf("title", "content"), bodyArray(request).single().jsonObject.keys)
        }

    @Test
    fun insertAllKeepsTheKeysOfEveryNoteInABatchIdentical() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(201))

            dataSource.insertAll(
                listOf(
                    NoteInsert("a", "c", pinned = true),
                    NoteInsert("b", null, tags = listOf("x")),
                    NoteInsert("c", "c", archived = true),
                ),
            )

            val sent = bodyArray(server.takeRequest()).map { it.jsonObject }
            val expectedKeys = setOf("title", "content", "pinned", "archived", "tags")
            assertTrue(sent.all { it.keys == expectedKeys })
            assertEquals(JsonNull, sent[1].getValue("content"))
            assertFalse(sent[1].getValue("pinned").jsonPrimitive.boolean)
        }

    @Test
    fun insertAllDecidesTheOptionalKeysPerBatch() =
        runTest {
            repeat(2) { server.enqueue(MockResponse().setResponseCode(201)) }
            val pinnedFirst = NoteInsert("p", "c", pinned = true)
            val plain = List(IMPORT_BATCH - 1) { NoteInsert("n$it", "c") }
            val secondBatch = List(5) { NoteInsert("m$it", "c") }

            dataSource.insertAll(listOf(pinnedFirst) + plain + secondBatch)

            val first = bodyArray(server.takeRequest()).map { it.jsonObject.keys }.toSet()
            val second = bodyArray(server.takeRequest()).map { it.jsonObject.keys }.toSet()
            assertEquals(setOf(setOf("title", "content", "pinned")), first)
            assertEquals(setOf(setOf("title", "content")), second)
        }

    @Test
    fun insertAllWithNothingSendsNothing() =
        runTest {
            assertEquals(0, dataSource.insertAll(emptyList()))
            assertEquals(0, server.requestCount)
        }

    @Test
    fun aFailureAfterSomeBatchesIsAPartialImport() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(201))
            server.enqueue(MockResponse().setResponseCode(500).setBody("""{"message":"boom"}"""))

            val error =
                failureOf {
                    dataSource.insertAll(
                        List(450) { NoteInsert("n$it", "c") },
                    )
                } as PartialImportException

            assertEquals(200, error.imported)
            assertEquals(450, error.total)
            assertEquals(500, (error.cause as ApiException).code)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun aFailureOfTheFirstBatchIsNotPartial() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500))

            val error = failureOf { dataSource.insertAll(List(450) { NoteInsert("n$it", "c") }) }

            assertTrue(error is ApiException)
        }

    @Test
    fun anExpiredTokenIsRefreshedAndTheRequestRetried() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(401))
            server.enqueue(ok("""{"access_token":"new-token","refresh_token":"refresh-2"}"""))
            server.enqueue(ok(rows(1)))

            assertEquals(1, dataSource.fetchPage(0).size)

            assertEquals("Bearer user-token", server.takeRequest().getHeader("Authorization"))
            server.takeRequest()
            assertEquals("Bearer new-token", server.takeRequest().getHeader("Authorization"))
            assertEquals("new-token", sessionStore.current()?.accessToken)
        }

    @Test
    fun redirectsAreNotFollowed() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(307).setHeader("Location", server.url("/elsewhere").toString()),
            )

            val error = failureOf { dataSource.fetchPage(0) } as ApiException

            assertEquals(307, error.code)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun anUnreachableServerIsAConnectivityProblem() =
        runTest {
            server.shutdown()

            assertTrue(NetworkErrors.isConnectivityProblem(failureOf { dataSource.fetchPage(0) }))
        }

    @Test
    fun anUnreadableAnswerIsAnIoProblem() =
        runTest {
            server.enqueue(ok("<html>Wi-Fi login</html>"))
            server.enqueue(ok("""{"not":"an array"}"""))

            assertTrue(failureOf { dataSource.fetchPage(0) } is IOException)
            assertTrue(failureOf { dataSource.fetchPage(0) } is IOException)
        }

    @Test
    fun withoutAConnectionNothingIsSent() =
        runTest {
            connectionStore.clear()

            assertTrue(failureOf { dataSource.fetchPage(0) } is ConnectionNotConfiguredException)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun withoutASessionNothingIsSent() =
        runTest {
            sessionStore.clear()

            assertTrue(failureOf { dataSource.fetchPage(0) } is NotSignedInException)
            assertEquals(0, server.requestCount)
        }

    private companion object {
        const val ANON_KEY = TestBackend.ANON_KEY
        const val IMPORT_BATCH = NoteRemoteDataSource.IMPORT_BATCH_SIZE
    }
}
