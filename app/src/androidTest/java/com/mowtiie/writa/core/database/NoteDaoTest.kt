package com.mowtiie.writa.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NoteDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: NoteDao

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = database.noteDao()
    }

    @After
    fun tearDown() = database.close()

    private fun row(
        id: Long,
        position: Long = id,
        archived: Boolean = false,
        deletedAt: String? = null,
        dirtyMask: Int = 0,
        pendingDelete: Boolean = false,
        syncFailed: Boolean = false,
    ) = NoteEntity(
        id = id,
        position = position,
        createdAt = "2026-11-01T10:00:00+00:00",
        updatedAt = null,
        deletedAt = deletedAt,
        pinned = false,
        archived = archived,
        payload = "payload-$id",
        dirtyMask = dirtyMask,
        pendingDelete = pendingDelete,
        syncFailed = syncFailed,
    )

    @Test
    fun aRowSurvivesTheRoundTripWithEveryField() =
        runTest {
            val original =
                NoteEntity(
                    id = 9,
                    position = 3,
                    createdAt = "c",
                    updatedAt = "u",
                    deletedAt = "d",
                    pinned = true,
                    archived = true,
                    payload = "p",
                    dirtyMask = DirtyMask.TEXT or DirtyMask.TAGS,
                    pendingDelete = true,
                    syncFailed = true,
                    baseUpdatedAt = "b",
                    clientId = "client",
                    version = 12,
                )

            dao.insert(original)

            assertEquals(original, dao.get(9))
        }

    @Test
    fun activeNotesAreOrderedByPositionAndExcludeArchivedTrashedAndPendingDeletes() =
        runTest {
            dao.insertAll(
                listOf(
                    row(1, position = 5),
                    row(2, position = -3),
                    row(3, position = 0, archived = true),
                    row(4, position = 1, deletedAt = "d"),
                    row(5, position = 2, pendingDelete = true),
                    row(6, position = 4),
                ),
            )

            assertEquals(listOf(2L, 6L, 1L), dao.observeActive().first().map { it.id })
        }

    @Test
    fun archivedNotesExcludeTrashedOnesAndPendingDeletes() =
        runTest {
            dao.insertAll(
                listOf(
                    row(1, archived = true),
                    row(2, archived = true, deletedAt = "d"),
                    row(3, archived = true, pendingDelete = true),
                    row(4),
                ),
            )

            assertEquals(listOf(1L), dao.observeArchived().first().map { it.id })
        }

    @Test
    fun trashedNotesIncludeArchivedOnesButNotPendingDeletes() =
        runTest {
            dao.insertAll(
                listOf(
                    row(1, deletedAt = "d"),
                    row(2, archived = true, deletedAt = "d"),
                    row(3, deletedAt = "d", pendingDelete = true),
                    row(4),
                ),
            )

            assertEquals(listOf(1L, 2L), dao.observeTrashed().first().map { it.id })
        }

    @Test
    fun observersEmitAgainWhenTheTableChanges() =
        runTest {
            dao.observeActive().test {
                assertTrue(awaitItem().isEmpty())
                dao.insert(row(1))
                assertEquals(listOf(1L), awaitItem().map { it.id })
                dao.insert(row(1, archived = true))
                assertTrue(awaitItem().isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun pendingListsUnsentWorkAndSkipsFailedNotes() =
        runTest {
            dao.insertAll(
                listOf(
                    row(1),
                    row(2, dirtyMask = DirtyMask.PINNED),
                    row(3, pendingDelete = true),
                    row(-2),
                    row(4, dirtyMask = DirtyMask.TEXT, syncFailed = true),
                ),
            )

            assertEquals(setOf(2L, 3L, -2L), dao.pending().map { it.id }.toSet())
        }

    @Test
    fun pendingCountIncludesFailedNotes() =
        runTest {
            dao.insertAll(
                listOf(row(1), row(2, dirtyMask = 1), row(3, pendingDelete = true), row(-2), row(4, syncFailed = true)),
            )

            assertEquals(4, dao.observePendingCount().first())
        }

    @Test
    fun pendingCountFollowsChanges() =
        runTest {
            dao.observePendingCount().test {
                assertEquals(0, awaitItem())
                dao.insert(row(1, dirtyMask = DirtyMask.TEXT))
                assertEquals(1, awaitItem())
                dao.delete(1)
                assertEquals(0, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun insertReplacesARowWithTheSameId() =
        runTest {
            dao.insert(row(1))
            dao.insert(row(1, position = 99))

            assertEquals(1, dao.everything().size)
            assertEquals(99L, dao.positionOf(1))
        }

    @Test
    fun positionsAndIdsReportTheirExtremes() =
        runTest {
            assertNull(dao.minPosition())
            assertNull(dao.lowestUsedId())

            dao.insertAll(listOf(row(5, position = 2), row(-3, position = -7)))

            assertEquals(-7L, dao.minPosition())
            assertEquals(-3L, dao.lowestUsedId())
        }

    @Test
    fun lowestUsedIdCountsIdsThatWereReplacedByTheServer() =
        runTest {
            dao.insert(row(975))
            dao.putAlias(IdAlias(oldId = -4, newId = 975))

            assertEquals(-4L, dao.lowestUsedId())
        }

    @Test
    fun setPositionAndSetFailedChangeOnlyThatRow() =
        runTest {
            dao.insertAll(listOf(row(1, position = 1), row(2, position = 2)))

            dao.setPosition(1, 10)
            dao.setFailed(2, true)

            assertEquals(10L, dao.get(1)?.position)
            assertEquals(false, dao.get(1)?.syncFailed)
            assertEquals(true, dao.get(2)?.syncFailed)
            assertEquals(2L, dao.get(2)?.position)
        }

    @Test
    fun deleteAndDeleteAllRemoveRows() =
        runTest {
            dao.insertAll(listOf(row(1), row(2), row(3)))

            dao.delete(2)
            assertEquals(listOf(1L, 3L), dao.everything().map { it.id }.sorted())

            dao.deleteAll()
            assertTrue(dao.everything().isEmpty())
        }

    @Test
    fun aliasesMapOldIdsToNewOnesAndCanBeCleared() =
        runTest {
            dao.putAlias(IdAlias(oldId = -2, newId = 77))

            assertEquals(77L, dao.aliasOf(-2))
            assertNull(dao.aliasOf(-3))

            dao.deleteAliases()
            assertNull(dao.aliasOf(-2))
        }

    @Test
    fun observedAliasesFollowTheTable() =
        runTest {
            dao.observeAliases().test {
                assertTrue(awaitItem().isEmpty())
                dao.putAlias(IdAlias(oldId = -2, newId = 77))
                assertEquals(listOf(IdAlias(-2, 77)), awaitItem())
                dao.deleteAliases()
                assertTrue(awaitItem().isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun metaIsStoredReplacedAndCleared() =
        runTest {
            assertNull(dao.meta("owner"))

            dao.putMeta(CacheMeta("owner", "a"))
            dao.putMeta(CacheMeta("owner", "b"))
            assertEquals("b", dao.meta("owner"))

            dao.deleteMeta()
            assertNull(dao.meta("owner"))
        }
}
