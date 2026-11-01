package com.mowtiie.writa.feature.notes.cache

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.mowtiie.writa.core.database.AppDatabase
import com.mowtiie.writa.core.database.DirtyMask
import com.mowtiie.writa.core.database.NoteDao
import com.mowtiie.writa.core.database.RoomTransactionRunner
import com.mowtiie.writa.core.network.apiJson
import com.mowtiie.writa.core.security.KeystoreKeys
import com.mowtiie.writa.core.security.KeystoreNoteCrypto
import com.mowtiie.writa.feature.notes.Note
import com.mowtiie.writa.feature.notes.NoteCodec
import com.mowtiie.writa.feature.notes.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.util.UUID

class NoteCacheRoomTest {
    private val keyAlias = "test_cache_key_${UUID.randomUUID()}"
    private val owner = MutableStateFlow<String?>("owner-a")
    private lateinit var database: AppDatabase
    private lateinit var dao: NoteDao
    private lateinit var transactions: RoomTransactionRunner
    private lateinit var codec: NoteCodec

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = database.noteDao()
        transactions = RoomTransactionRunner(database)
        codec = NoteCodec(KeystoreNoteCrypto(keyAlias), apiJson)
    }

    @After
    fun tearDown() {
        database.close()
        KeystoreKeys.delete(keyAlias)
    }

    private fun newCache() =
        NoteCache(
            dao = dao,
            transactions = transactions,
            codec = codec,
            ownerSource =
                object : CacheOwnerSource {
                    override val owner = this@NoteCacheRoomTest.owner
                },
            clock = Clock.systemUTC(),
            clientIds = UuidClientIdGenerator(),
            defaultDispatcher = Dispatchers.Default,
        )

    private fun serverNote(
        id: Long,
        title: String = "server $id",
    ) = Note(
        id,
        title,
        "body",
        listOf("tag"),
        false,
        false,
        "2026-11-01T10:00:00+00:00",
        "2026-11-01T10:00:00+00:00",
        null,
    )

    @Test
    fun aCreatedNoteIsEncryptedAtRestAndShownDecrypted() =
        runTest {
            val cache = newCache()

            val created = cache.createLocal("Private title", "Private body", listOf("secret-tag"), pinned = false)

            val stored = dao.get(created.id)!!
            assertFalse(stored.payload.contains("Private"))
            assertFalse(stored.payload.contains("secret-tag"))
            val shown = cache.observeActive().first().single()
            assertEquals("Private title", shown.title)
            assertEquals("Private body", shown.content)
            assertEquals(listOf("secret-tag"), shown.tags)
            assertEquals(SyncState.Pending, shown.syncState)
        }

    @Test
    fun theListUpdatesWhenTheCacheChanges() =
        runTest {
            val cache = newCache()

            cache.observeActive().test {
                assertTrue(awaitItem().isEmpty())
                val created = cache.createLocal("one", null, emptyList(), false)
                assertEquals(listOf(created.id), awaitItem().map { it.id })
                cache.deleteLocal(created.id)
                assertTrue(awaitItem().isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aRowMovedToAnotherIdIsUnreadableAndSkipped() =
        runTest {
            val cache = newCache()
            cache.mergeServerNotes(listOf(serverNote(1), serverNote(2)))
            val first = dao.get(1)!!
            dao.insert(dao.get(2)!!.copy(payload = first.payload))

            assertEquals(listOf(1L), cache.observeActive().first().map { it.id })
        }

    @Test
    fun aNewOwnerFindsAnEmptyCacheEvenWithTheSameDatabase() =
        runTest {
            newCache().mergeServerNotes(listOf(serverNote(1)))
            assertEquals(1, dao.everything().size)

            owner.value = "owner-b"

            assertNull(newCache().get(1))
            assertTrue(dao.everything().isEmpty())
        }

    @Test
    fun theSameOwnerOnANewInstanceKeepsItsNotes() =
        runTest {
            newCache().mergeServerNotes(listOf(serverNote(1)))

            assertEquals("server 1", newCache().get(1)?.title)
        }

    @Test
    fun aTransactionThatFailsHalfwayChangesNothing() =
        runTest {
            val row = codec.encode(serverNote(1), 0)

            runCatching {
                transactions.run {
                    dao.insert(row)
                    check(false) { "boom" }
                }
            }

            assertNull(dao.get(1))
        }

    @Test
    fun anUploadedNewNoteSwapsItsIdAtomicallyAndKeepsAnAlias() =
        runTest {
            val cache = newCache()
            val created = cache.createLocal("draft", null, emptyList(), false)

            cache.syncSucceeded(created.id, version = 1, server = serverNote(100, title = "draft"))

            assertNull(dao.get(created.id))
            assertEquals(100L, dao.aliasOf(created.id))
            assertEquals(SyncState.Synced, cache.get(100)?.syncState)
            assertEquals(100L, cache.get(created.id)?.id)
            assertEquals(0, cache.observePendingCount().first())
        }

    @Test
    fun aMergeKeepsUnsentEditsAcrossARealRefresh() =
        runTest {
            val cache = newCache()
            cache.mergeServerNotes(listOf(serverNote(1), serverNote(2)))
            cache.saveLocal(serverNote(1, title = "my edit"), DirtyMask.TEXT)

            cache.mergeServerNotes(
                listOf(serverNote(2, title = "changed on server"), serverNote(1, title = "server copy")),
            )

            val titles = cache.observeActive().first().associate { it.id to it.title }
            assertEquals("my edit", titles[1])
            assertEquals("changed on server", titles[2])
            assertNotNull(cache.pending().singleOrNull { it.note.id == 1L })
        }
}
