package com.mowtiie.writa.feature.notes.cache

import app.cash.turbine.test
import com.mowtiie.writa.core.database.CacheMeta
import com.mowtiie.writa.core.database.DirtyMask
import com.mowtiie.writa.core.database.FakeNoteDao
import com.mowtiie.writa.core.database.IdAlias
import com.mowtiie.writa.core.database.NoteEntity
import com.mowtiie.writa.core.database.PassThroughTransactionRunner
import com.mowtiie.writa.core.network.apiJson
import com.mowtiie.writa.core.security.FakeNoteCrypto
import com.mowtiie.writa.feature.notes.Note
import com.mowtiie.writa.feature.notes.NoteCodec
import com.mowtiie.writa.feature.notes.SyncState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.GeneralSecurityException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class NoteCacheTest {
    private val dao = FakeNoteDao()
    private val crypto = FakeNoteCrypto()
    private val codec = NoteCodec(crypto, apiJson)
    private val owner = MutableStateFlow<String?>(OWNER)
    private val ownerSource =
        object : CacheOwnerSource {
            override val owner = this@NoteCacheTest.owner
        }
    private var clientIdCounter = 0

    @Before
    fun storeTheOwner() = runBlocking { dao.putMeta(CacheMeta("owner", OWNER)) }

    private fun TestScope.cache() =
        NoteCache(
            dao = dao,
            transactions = PassThroughTransactionRunner(),
            codec = codec,
            ownerSource = ownerSource,
            clock = Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC),
            clientIds = ClientIdGenerator { "client-${++clientIdCounter}" },
            defaultDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

    private fun note(
        id: Long,
        title: String = "t$id",
        archived: Boolean = false,
        deletedAt: String? = null,
        updatedAt: String? = "2026-11-01T10:00:00+00:00",
    ) = Note(id, title, "c$id", emptyList(), false, archived, "2026-11-01T10:00:00+00:00", updatedAt, deletedAt)

    private suspend fun seed(
        note: Note,
        position: Long = note.id,
        change: (NoteEntity) -> NoteEntity = { it },
    ) = dao.insert(change(codec.encode(note, position)))

    private suspend fun raw(id: Long) = dao.get(id)

    // ---------------------------- owner ----------------------------

    @Test
    fun aDifferentOwnerWipesTheCacheBeforeAnythingIsRead() =
        runTest {
            dao.putMeta(CacheMeta("owner", "someone-else"))
            seed(note(1))
            dao.putAlias(IdAlias(-2, 1))

            assertNull(cache().get(1))

            assertTrue(dao.rows.isEmpty())
            assertTrue(dao.aliases.isEmpty())
            assertEquals(OWNER, dao.metaValues["owner"])
        }

    @Test
    fun theSameOwnerKeepsItsNotes() =
        runTest {
            seed(note(1))

            assertEquals("t1", cache().get(1)?.title)
        }

    @Test
    fun aNeverUsedCacheIsClaimedByTheFirstOwner() =
        runTest {
            dao.deleteMeta()
            seed(note(1))

            assertTrue(cache().observeActive().first().isEmpty())
            assertEquals(OWNER, dao.metaValues["owner"])
        }

    @Test
    fun observersNeverShowAnotherOwnersNotes() =
        runTest {
            dao.putMeta(CacheMeta("owner", "someone-else"))
            seed(note(1))

            assertTrue(cache().observeActive().first().isEmpty())
        }

    @Test
    fun switchingTheOwnerWhileObservingWipesAndRestartsTheFlow() =
        runTest {
            seed(note(1))
            val cache = cache()

            cache.observeActive().test {
                assertEquals(listOf(1L), awaitItem().map { it.id })
                owner.value = "owner-b"
                assertTrue(awaitItem().isEmpty())
                assertTrue(dao.rows.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun withoutAnOwnerReadsAreEmptyAndWritesFail() =
        runTest {
            owner.value = null
            seed(note(1))
            val cache = cache()

            assertTrue(cache.observeActive().first().isEmpty())
            assertTrue(cache.observeArchived().first().isEmpty())
            assertTrue(cache.observeTrashed().first().isEmpty())
            assertEquals(0, cache.observePendingCount().first())
            assertNull(cache.get(1))
            assertTrue(cache.pending().isEmpty())
            assertEquals(7L, cache.resolveId(7))
            assertThrows(
                CacheUnavailableException::class.java,
            ) { runBlocking { cache.createLocal("t", null, emptyList(), false) } }
            assertThrows(CacheUnavailableException::class.java) { runBlocking { cache.mergeServerNotes(emptyList()) } }
            assertEquals(1, dao.rows.size)
        }

    @Test
    fun clearForgetsEverythingIncludingTheOwner() =
        runTest {
            seed(note(1))
            dao.putAlias(IdAlias(-2, 1))
            val cache = cache()
            cache.get(1)

            cache.clear()

            assertTrue(dao.rows.isEmpty())
            assertTrue(dao.aliases.isEmpty())
            assertTrue(dao.metaValues.isEmpty())
            assertNull(cache.get(1))
            assertEquals(OWNER, dao.metaValues["owner"])
        }

    // ---------------------------- reading ----------------------------

    @Test
    fun theListsAreOrderedByServerPositionAndSplitByState() =
        runTest {
            seed(note(1), position = 5)
            seed(note(2), position = -3)
            seed(note(3, archived = true), position = 0)
            seed(note(4, deletedAt = "2026-11-02T00:00:00+00:00"), position = 1)
            seed(note(5), position = 2) { it.copy(pendingDelete = true) }
            seed(note(6, archived = true, deletedAt = "2026-11-02T00:00:00+00:00"), position = 3)
            val cache = cache()

            assertEquals(listOf(2L, 1L), cache.observeActive().first().map { it.id })
            assertEquals(listOf(3L), cache.observeArchived().first().map { it.id })
            assertEquals(listOf(4L, 6L), cache.observeTrashed().first().map { it.id })
        }

    @Test
    fun unreadableRowsAreSkipped() =
        runTest {
            seed(note(1))
            dao.insert(codec.encode(note(2), 2).copy(payload = "garbage"))

            assertEquals(listOf(1L), cache().observeActive().first().map { it.id })
        }

    @Test
    fun getFollowsAnAliasAndHidesPendingDeletes() =
        runTest {
            seed(note(100))
            seed(note(7)) { it.copy(pendingDelete = true) }
            dao.putAlias(IdAlias(-2, 100))
            val cache = cache()

            assertEquals(100L, cache.get(-2)?.id)
            assertEquals(100L, cache.resolveId(-2))
            assertNull(cache.get(7))
            assertNull(cache.get(999))
        }

    @Test
    fun aliasesAreFollowedAcrossSeveralHops() =
        runTest {
            seed(note(300))
            dao.putAlias(IdAlias(-2, -3))
            dao.putAlias(IdAlias(-3, 300))

            assertEquals(300L, cache().resolveId(-2))
        }

    // ---------------------------- merging what the server sent ----------------------------

    @Test
    fun theServerNotesAreStoredInOrderIncludingTrashedAndArchivedOnes() =
        runTest {
            val cache = cache()

            cache.mergeServerNotes(
                listOf(note(30), note(20, archived = true), note(10, deletedAt = "2026-11-02T00:00:00+00:00")),
            )

            assertEquals(listOf(30L, 20L, 10L), dao.rows.sortedBy { it.position }.map { it.id })
            assertEquals(listOf(0L, 1L, 2L), dao.rows.sortedBy { it.position }.map { it.position })
            assertEquals(3, dao.rows.size)
        }

    @Test
    fun syncedNotesAreReplacedAndTheOnesTheServerDroppedAreRemoved() =
        runTest {
            seed(note(1, title = "old"))
            seed(note(2))

            cache().mergeServerNotes(listOf(note(1, title = "new")))

            assertEquals("new", codec.decode(raw(1)!!)?.title)
            assertNull(raw(2))
        }

    @Test
    fun noteWithLocalChangesKeepTheirLocalVersionAndOnlyFollowTheOrder() =
        runTest {
            seed(note(1, title = "mine"), position = 9) { it.copy(dirtyMask = DirtyMask.TEXT, version = 3) }
            seed(note(2, title = "mine"), position = 9) { it.copy(syncFailed = true) }
            seed(note(3, title = "mine"), position = 9) { it.copy(pendingDelete = true) }

            cache().mergeServerNotes(
                listOf(note(3, title = "server"), note(2, title = "server"), note(1, title = "server")),
            )

            for (id in 1L..3L) assertEquals("mine", codec.decode(raw(id)!!)?.title)
            assertEquals(listOf(2L, 1L, 0L), listOf(1L, 2L, 3L).map { raw(it)!!.position })
            assertEquals(3L, raw(1)!!.version)
        }

    @Test
    fun notesOnlyOnThisDeviceAndUnsentChangesSurviveAMergeThatLacksThem() =
        runTest {
            seed(note(-2), position = -1)
            seed(note(5), position = 5) { it.copy(dirtyMask = DirtyMask.PINNED) }
            seed(note(6), position = 6) { it.copy(syncFailed = true) }
            seed(note(7), position = 7)

            cache().mergeServerNotes(emptyList())

            assertEquals(setOf(-2L, 5L, 6L), dao.rows.map { it.id }.toSet())
        }

    @Test
    fun nothingIsWrittenWhenANoteCannotBeEncrypted() =
        runTest {
            seed(note(1, title = "old"))
            seed(note(2))
            val cache = cache()
            crypto.failToEncrypt = true

            assertThrows(GeneralSecurityException::class.java) {
                runBlocking { cache.mergeServerNotes(listOf(note(1, title = "new"))) }
            }

            crypto.failToEncrypt = false
            assertEquals("old", codec.decode(raw(1)!!)?.title)
            assertNotNull(raw(2))
        }

    // ---------------------------- creating ----------------------------

    @Test
    fun aNewNoteGetsATemporaryIdAClientIdAndAPositionAboveTheServerNotes() =
        runTest {
            seed(note(5), position = 0)
            val cache = cache()

            val created = cache.createLocal("Title", "Body", listOf("#Work", " work", "Home"), pinned = true)

            assertEquals(-2L, created.id)
            assertEquals("Title", created.title)
            assertEquals(listOf("Work", "Home"), created.tags)
            assertTrue(created.pinned)
            assertEquals(NOW, created.createdAt)
            assertNull(created.updatedAt)
            assertEquals(SyncState.Pending, created.syncState)
            val row = raw(-2)!!
            assertEquals("client-1", row.clientId)
            assertEquals(1L, row.version)
            assertEquals(-1L, row.position)
        }

    @Test
    fun temporaryIdsCountDownAndNeverReachMinusOne() =
        runTest {
            val cache = cache()

            val ids = List(3) { cache.createLocal("n$it", null, emptyList(), false).id }

            assertEquals(listOf(-2L, -3L, -4L), ids)
            assertEquals(listOf(-1L, -2L, -3L), ids.map { raw(it)!!.position })
            assertEquals(
                3,
                dao.rows
                    .map { it.clientId }
                    .toSet()
                    .size,
            )
        }

    @Test
    fun aNewNoteShowsUpFirstInTheList() =
        runTest {
            seed(note(5), position = 0)
            val cache = cache()

            cache.createLocal("fresh", null, emptyList(), false)

            assertEquals(listOf(-2L, 5L), cache.observeActive().first().map { it.id })
        }

    // ---------------------------- editing ----------------------------

    @Test
    fun theFirstEditOfASyncedNoteRemembersTheServerVersionItWasBasedOn() =
        runTest {
            seed(note(5, updatedAt = "2026-11-01T11:00:00+00:00"))
            val cache = cache()

            val saved = cache.saveLocal(note(5, title = "edited", updatedAt = "ignored"), DirtyMask.TEXT)

            assertEquals("edited", saved?.title)
            assertEquals("2026-11-01T11:00:00+00:00", saved?.updatedAt)
            assertEquals(SyncState.Pending, saved?.syncState)
            val row = raw(5)!!
            assertEquals(DirtyMask.TEXT, row.dirtyMask)
            assertEquals("2026-11-01T11:00:00+00:00", row.baseUpdatedAt)
            assertEquals(1L, row.version)
        }

    @Test
    fun laterEditsAddToTheMaskKeepTheFirstBaseAndBumpTheVersion() =
        runTest {
            seed(note(5, updatedAt = "2026-11-01T11:00:00+00:00"))
            val cache = cache()

            cache.saveLocal(note(5, title = "one"), DirtyMask.TEXT)
            cache.saveLocal(note(5, title = "two").copy(pinned = true), DirtyMask.PINNED)

            val row = raw(5)!!
            assertEquals(DirtyMask.TEXT or DirtyMask.PINNED, row.dirtyMask)
            assertEquals("2026-11-01T11:00:00+00:00", row.baseUpdatedAt)
            assertEquals(2L, row.version)
            assertEquals("two", codec.decode(row)?.title)
            assertTrue(codec.decode(row)!!.pinned)
        }

    @Test
    fun createdAtAndUpdatedAtAlwaysComeFromTheStoredNote() =
        runTest {
            seed(note(5, updatedAt = "2026-11-01T11:00:00+00:00"))

            val saved = cache().saveLocal(note(5).copy(createdAt = "forged", updatedAt = "forged"), DirtyMask.TAGS)

            assertEquals("2026-11-01T10:00:00+00:00", saved?.createdAt)
            assertEquals("2026-11-01T11:00:00+00:00", saved?.updatedAt)
        }

    @Test
    fun aNoteOnlyOnThisDeviceHasNoBase() =
        runTest {
            val cache = cache()
            val created = cache.createLocal("t", null, emptyList(), false)

            cache.saveLocal(created.copy(title = "edited"), DirtyMask.TEXT)

            assertNull(raw(created.id)!!.baseUpdatedAt)
            assertEquals(2L, raw(created.id)!!.version)
        }

    @Test
    fun editingAFailedNoteTriesItAgainButKeepsItsBase() =
        runTest {
            seed(note(5)) { it.copy(dirtyMask = DirtyMask.TEXT, syncFailed = true, baseUpdatedAt = "the-base") }

            cache().saveLocal(note(5, title = "again"), DirtyMask.TEXT)

            val row = raw(5)!!
            assertFalse(row.syncFailed)
            assertEquals("the-base", row.baseUpdatedAt)
        }

    @Test
    fun savingAnUnknownOrDeletedNoteDoesNothing() =
        runTest {
            seed(note(5)) { it.copy(pendingDelete = true) }
            val cache = cache()

            assertNull(cache.saveLocal(note(999), DirtyMask.TEXT))
            assertNull(cache.saveLocal(note(5, title = "x"), DirtyMask.TEXT))
            assertEquals(1, dao.rows.size)
            assertEquals(0L, raw(5)!!.version)
        }

    @Test
    fun savingThroughATemporaryIdReachesTheUploadedNote() =
        runTest {
            seed(note(100))
            dao.putAlias(IdAlias(-2, 100))

            val saved = cache().saveLocal(note(-2, title = "via alias"), DirtyMask.TEXT)

            assertEquals(100L, saved?.id)
            assertEquals("via alias", codec.decode(raw(100)!!)?.title)
        }

    @Test
    fun aFailedEncryptionLeavesTheStoredNoteUntouched() =
        runTest {
            seed(note(5, title = "stored"))
            val cache = cache()
            crypto.failToEncrypt = true

            assertThrows(GeneralSecurityException::class.java) {
                runBlocking { cache.saveLocal(note(5, title = "lost"), DirtyMask.TEXT) }
            }

            crypto.failToEncrypt = false
            assertEquals("stored", codec.decode(raw(5)!!)?.title)
            assertEquals(0, raw(5)!!.dirtyMask)
        }

    // ---------------------------- deleting ----------------------------

    @Test
    fun deletingASyncedNoteHidesItAndLeavesTheServerDeleteToUpload() =
        runTest {
            seed(note(5)) { it.copy(syncFailed = true, version = 2) }
            val cache = cache()

            assertTrue(cache.deleteLocal(5))

            val row = raw(5)!!
            assertTrue(row.pendingDelete)
            assertFalse(row.syncFailed)
            assertEquals(3L, row.version)
            assertTrue(cache.observeActive().first().isEmpty())
            assertNull(cache.get(5))
            assertEquals(1, cache.observePendingCount().first())
        }

    @Test
    fun deletingANoteThatNeverReachedTheServerRemovesItAtOnce() =
        runTest {
            val cache = cache()
            val created = cache.createLocal("t", null, emptyList(), false)

            assertTrue(cache.deleteLocal(created.id))

            assertNull(raw(created.id))
            assertEquals(0, cache.observePendingCount().first())
        }

    @Test
    fun deletingAnUnknownNoteReportsFalse() =
        runTest {
            assertFalse(cache().deleteLocal(404))
        }

    // ---------------------------- uploading ----------------------------

    @Test
    fun pendingListsNewNotesFirstOldestFirstThenChangesInIdOrder() =
        runTest {
            seed(note(9), position = 9) { it.copy(dirtyMask = DirtyMask.PINNED) }
            seed(note(4), position = 4) { it.copy(pendingDelete = true) }
            seed(note(-3), position = -2)
            seed(note(-2), position = -1)
            seed(note(7), position = 7)
            seed(note(8), position = 8) { it.copy(dirtyMask = DirtyMask.TEXT, syncFailed = true) }

            assertEquals(listOf(-2L, -3L, 4L, 9L), cache().pending().map { it.note.id })
        }

    @Test
    fun pendingCarriesWhatTheSyncEngineNeeds() =
        runTest {
            seed(note(5)) {
                it.copy(
                    dirtyMask = DirtyMask.TEXT or DirtyMask.TRASH,
                    baseUpdatedAt = "base",
                    clientId = "client",
                    version = 4,
                )
            }

            val change = cache().pending().single()

            assertEquals(5L, change.note.id)
            assertEquals(DirtyMask.TEXT or DirtyMask.TRASH, change.mask)
            assertEquals("base", change.baseUpdatedAt)
            assertEquals("client", change.clientId)
            assertEquals(4L, change.version)
            assertFalse(change.pendingDelete)
        }

    @Test
    fun pendingSkipsRowsThatCannotBeRead() =
        runTest {
            dao.insert(codec.encode(note(5), 5).copy(dirtyMask = DirtyMask.TEXT, payload = "garbage"))

            assertTrue(cache().pending().isEmpty())
        }

    @Test
    fun anUploadedNewNoteTakesTheServersIdAndLeavesAnAlias() =
        runTest {
            val cache = cache()
            val created = cache.createLocal("t", null, emptyList(), false)

            cache.syncSucceeded(created.id, version = 1, server = note(100, title = "t", updatedAt = "server-time"))

            assertNull(raw(-2))
            val row = raw(100)!!
            assertEquals(0, row.dirtyMask)
            assertEquals(0L, row.version)
            assertEquals(-1L, row.position)
            assertEquals(SyncState.Synced, cache.get(100)?.syncState)
            assertEquals(100L, cache.get(-2)?.id)
            assertEquals(100L, cache.resolveId(-2))
        }

    @Test
    fun anUploadedChangeReplacesTheLocalCopyWithTheServers() =
        runTest {
            seed(note(5, updatedAt = "old"), position = 3) {
                it.copy(dirtyMask = DirtyMask.TEXT, baseUpdatedAt = "old", version = 2)
            }

            cache().syncSucceeded(5, version = 2, server = note(5, title = "from server", updatedAt = "new"))

            val row = raw(5)!!
            assertEquals(0, row.dirtyMask)
            assertNull(row.baseUpdatedAt)
            assertEquals("new", row.updatedAt)
            assertEquals(3L, row.position)
            assertEquals("from server", codec.decode(row)?.title)
        }

    @Test
    fun aNoteEditedAgainWhileUploadingKeepsTheNewerTextForTheNextUpload() =
        runTest {
            seed(note(5, updatedAt = "old"), position = 3)
            val cache = cache()
            cache.saveLocal(note(5, title = "first"), DirtyMask.TEXT)
            cache.saveLocal(note(5, title = "second"), DirtyMask.TEXT)

            cache.syncSucceeded(5, version = 1, server = note(5, title = "first", updatedAt = "after-first"))

            val row = raw(5)!!
            assertEquals("second", codec.decode(row)?.title)
            assertEquals(DirtyMask.TEXT, row.dirtyMask)
            assertEquals("after-first", row.baseUpdatedAt)
            assertEquals("after-first", row.updatedAt)
            assertEquals(2L, row.version)
            assertEquals(listOf(5L), cache.pending().map { it.note.id })
        }

    @Test
    fun aNewNoteEditedWhileItUploadedIsSentAgainInFullOnTopOfTheServersCopy() =
        runTest {
            val cache = cache()
            val created = cache.createLocal("first", null, emptyList(), false)
            cache.saveLocal(created.copy(title = "second"), DirtyMask.TEXT)

            cache.syncSucceeded(created.id, version = 1, server = note(100, title = "first", updatedAt = "server-time"))

            assertNull(raw(-2))
            val row = raw(100)!!
            assertEquals("second", codec.decode(row)?.title)
            assertEquals(DirtyMask.ALL, row.dirtyMask)
            assertEquals("server-time", row.baseUpdatedAt)
            assertEquals(100L, cache.resolveId(-2))
        }

    @Test
    fun aNoteDeletedWhileItsCreationUploadedHasTheNewServerCopyDeletedToo() =
        runTest {
            val cache = cache()
            val created = cache.createLocal("t", null, emptyList(), false)
            cache.deleteLocal(created.id)

            cache.syncSucceeded(created.id, version = 1, server = note(100))

            val row = raw(100)!!
            assertTrue(row.pendingDelete)
            assertEquals(listOf(100L), cache.pending().map { it.note.id })
            assertTrue(cache.observeActive().first().isEmpty())
        }

    @Test
    fun aSuccessForANoteThatIsGoneAndKeptItsIdDoesNothing() =
        runTest {
            cache().syncSucceeded(5, version = 1, server = note(5))

            assertTrue(dao.rows.isEmpty())
        }

    @Test
    fun aFailedNoteLeavesTheQueueButStaysCountedUntilRetried() =
        runTest {
            seed(note(5)) { it.copy(dirtyMask = DirtyMask.TEXT) }
            val cache = cache()

            cache.syncFailed(5)

            assertTrue(cache.pending().isEmpty())
            assertEquals(1, cache.observePendingCount().first())
            assertEquals(SyncState.Failed, cache.get(5)?.syncState)

            cache.retryFailed(5)

            assertEquals(listOf(5L), cache.pending().map { it.note.id })
            assertEquals(SyncState.Pending, cache.get(5)?.syncState)
        }

    @Test
    fun failureAndRetryFollowAnAlias() =
        runTest {
            seed(note(100)) { it.copy(dirtyMask = DirtyMask.TEXT) }
            dao.putAlias(IdAlias(-2, 100))
            val cache = cache()

            cache.syncFailed(-2)
            assertTrue(raw(100)!!.syncFailed)

            cache.retryFailed(-2)
            assertFalse(raw(100)!!.syncFailed)
        }

    @Test
    fun droppingANoteForgetsItEvenThroughAnAlias() =
        runTest {
            seed(note(100)) { it.copy(dirtyMask = DirtyMask.TEXT) }
            seed(note(6)) { it.copy(dirtyMask = DirtyMask.TEXT) }
            dao.putAlias(IdAlias(-2, 100))
            val cache = cache()

            cache.syncDropped(-2)
            cache.syncDropped(6)

            assertTrue(dao.rows.isEmpty())
        }

    @Test
    fun replacingWithTheServerDiscardsLocalChangesAndKeepsThePosition() =
        runTest {
            seed(note(5, title = "mine"), position = 8) {
                it.copy(
                    dirtyMask = DirtyMask.TEXT or DirtyMask.PINNED,
                    syncFailed = true,
                    baseUpdatedAt = "b",
                    version = 6,
                )
            }

            cache().replaceWithServer(5, note(5, title = "theirs", updatedAt = "newer"))

            val row = raw(5)!!
            assertEquals("theirs", codec.decode(row)?.title)
            assertEquals(0, row.dirtyMask)
            assertFalse(row.syncFailed)
            assertNull(row.baseUpdatedAt)
            assertEquals(0L, row.version)
            assertEquals(8L, row.position)
        }

    @Test
    fun aChangeToAnotherListDoesNotMakeThisListEmitAgain() =
        runTest {
            seed(note(1))
            val cache = cache()

            cache.observeActive().test {
                assertEquals(listOf(1L), awaitItem().map { it.id })
                seed(note(2, archived = true))
                expectNoEvents()
                seed(note(3))
                assertEquals(listOf(1L, 3L), awaitItem().map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun theSamePendingCountIsNotEmittedTwice() =
        runTest {
            seed(note(1))
            val cache = cache()

            cache.observePendingCount().test {
                assertEquals(0, awaitItem())
                seed(note(2))
                expectNoEvents()
                seed(note(3)) { it.copy(dirtyMask = DirtyMask.PINNED) }
                assertEquals(1, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun anUploadedNoteKeepsItsFirstIdAsItsListKey() =
        runTest {
            val cache = cache()
            val created = cache.createLocal("t", null, emptyList(), false)

            cache.observeStableKeys().test {
                assertEquals(emptyMap<Long, Long>(), awaitItem())
                cache.syncSucceeded(created.id, version = 1, server = note(100))
                assertEquals(mapOf(100L to created.id), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun withoutAnOwnerThereAreNoListKeys() =
        runTest {
            owner.value = null
            dao.putAlias(IdAlias(-2, 100))

            assertEquals(emptyMap<Long, Long>(), cache().observeStableKeys().first())
        }

    @Test
    fun clearingForgetsTheListKeys() =
        runTest {
            dao.putAlias(IdAlias(-2, 100))
            val cache = cache()
            assertEquals(mapOf(100L to -2L), cache.observeStableKeys().first())

            cache.clear()

            assertEquals(emptyMap<Long, Long>(), cache.observeStableKeys().first())
        }

    @Test
    fun pendingCountFollowsEveryKindOfUnsentWork() =
        runTest {
            val cache = cache()

            cache.observePendingCount().test {
                assertEquals(0, awaitItem())
                val created = cache.createLocal("t", null, emptyList(), false)
                assertEquals(1, awaitItem())
                cache.deleteLocal(created.id)
                assertEquals(0, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    private companion object {
        const val OWNER = "owner-a"
        const val NOW = "2026-11-05T12:00:00Z"
    }
}
