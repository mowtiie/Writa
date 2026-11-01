package com.mowtiie.writa.feature.notes

import com.mowtiie.writa.core.database.NoteEntity
import com.mowtiie.writa.core.network.apiJson
import com.mowtiie.writa.core.security.FakeNoteCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.GeneralSecurityException

class NoteCodecTest {
    private val codec = NoteCodec(FakeNoteCrypto(), apiJson)

    private val note =
        Note(
            id = 42,
            title = "Title é✓",
            content = "Body \"quoted\"\nline two",
            tags = listOf("work", "Home"),
            pinned = true,
            archived = true,
            createdAt = "2026-11-01T10:00:00+00:00",
            updatedAt = "2026-11-02T10:00:00+00:00",
            deletedAt = "2026-11-03T10:00:00+00:00",
        )

    @Test
    fun aNoteSurvivesAnEncodeDecodeRoundTrip() {
        assertEquals(note, codec.decode(codec.encode(note, position = 7)))
    }

    @Test
    fun aNoteWithoutContentOrTagsSurvivesToo() {
        val bare = note.copy(content = null, tags = emptyList(), updatedAt = null, deletedAt = null)

        assertEquals(bare, codec.decode(codec.encode(bare, position = 0)))
    }

    @Test
    fun theTextIsNotStoredInTheClear() {
        val row = codec.encode(note.copy(content = "never in plain sight"), position = 0)

        assertTrue(row.payload.startsWith("enc:42:"))
        // Only the fake crypto leaves it readable; the point is that no other column carries it.
        assertFalse(listOf(row.createdAt, row.updatedAt, row.deletedAt).any { it.orEmpty().contains("never") })
    }

    @Test
    fun plainColumnsAreStoredAsTheyAre() {
        val row = codec.encode(note, position = 7)

        assertEquals(42L, row.id)
        assertEquals(7L, row.position)
        assertEquals("2026-11-01T10:00:00+00:00", row.createdAt)
        assertEquals("2026-11-02T10:00:00+00:00", row.updatedAt)
        assertEquals("2026-11-03T10:00:00+00:00", row.deletedAt)
        assertTrue(row.pinned && row.archived)
    }

    @Test
    fun anEncodedRowStartsWithoutAnyLocalChanges() {
        val row = codec.encode(note, position = 0)

        assertEquals(0, row.dirtyMask)
        assertFalse(row.pendingDelete)
        assertFalse(row.syncFailed)
        assertNull(row.baseUpdatedAt)
        assertNull(row.clientId)
        assertEquals(0L, row.version)
        assertEquals(SyncState.Synced, codec.decode(row)?.syncState)
    }

    @Test
    fun aRowMovedToAnotherIdCannotBeDecrypted() {
        val row = codec.encode(note, position = 0)

        assertNull(codec.decode(row.copy(id = 43)))
    }

    @Test
    fun anUnreadablePayloadIsSkippedNotThrown() {
        val row = codec.encode(note, position = 0)

        assertNull(codec.decode(row.copy(payload = "garbage")))
        assertNull(codec.decode(row.copy(payload = "enc:42:not json")))
        assertNull(codec.decode(row.copy(payload = "enc:42:{\"title\":\"only a title\"}")))
    }

    @Test
    fun unknownPayloadKeysAreIgnored() {
        val row = codec.encode(note, position = 0)
        val extended = row.copy(payload = "enc:42:{\"title\":\"T\",\"content\":null,\"tags\":[],\"future\":1}")

        assertEquals("T", codec.decode(extended)?.title)
    }

    @Test
    fun anEncryptionFailureIsReportedNotSwallowed() {
        val failing = NoteCodec(FakeNoteCrypto().apply { failToEncrypt = true }, apiJson)

        try {
            failing.encode(note, position = 0)
            throw AssertionError("expected GeneralSecurityException")
        } catch (_: GeneralSecurityException) {
            assertNotNull(failing)
        }
    }

    @Test
    fun syncStateFollowsTheLocalChanges() {
        val synced = codec.encode(note, position = 0)

        assertEquals(SyncState.Pending, codec.decode(synced.copy(dirtyMask = 1))?.syncState)
        assertEquals(SyncState.Pending, codec.decode(synced.copy(pendingDelete = true))?.syncState)
        assertEquals(SyncState.Failed, codec.decode(synced.copy(syncFailed = true, dirtyMask = 1))?.syncState)
    }

    @Test
    fun aNoteCreatedOfflineIsPendingEvenWithoutAMask() {
        val offline: NoteEntity = codec.encode(note.copy(id = -2), position = -1)

        assertEquals(SyncState.Pending, codec.decode(offline)?.syncState)
    }
}
