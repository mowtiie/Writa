package com.mowtiie.writa.feature.notes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTest {
    private fun note(
        id: Long = 1,
        createdAt: String = "2026-11-01T10:00:00+00:00",
        updatedAt: String? = null,
        deletedAt: String? = null,
    ) = Note(id, "t", null, emptyList(), false, false, createdAt, updatedAt, deletedAt)

    @Test
    fun serverNotesAreNotLocalOnly() {
        assertFalse(note(id = 1).isLocalOnly)
        assertFalse(note(id = 0).isLocalOnly)
    }

    @Test
    fun temporaryIdsAreLocalOnlyButTheEditorsNoNoteIdIsNot() {
        assertTrue(note(id = -2).isLocalOnly)
        assertTrue(note(id = -57).isLocalOnly)
        assertFalse(note(id = -1).isLocalOnly)
    }

    @Test
    fun aNoteWithADeletedAtIsTrashed() {
        assertTrue(note(deletedAt = "2026-11-02T00:00:00+00:00").isTrashed)
        assertFalse(note(deletedAt = null).isTrashed)
    }

    @Test
    fun aNoteIsEditedOnceUpdatedAtIsAtLeastASecondAfterCreatedAt() {
        assertTrue(note(updatedAt = "2026-11-01T10:00:01+00:00").isEdited)
        assertTrue(note(updatedAt = "2026-11-01T10:05:00.5+00:00").isEdited)
        assertFalse(note(updatedAt = "2026-11-01T10:00:00.999999+00:00").isEdited)
        assertFalse(note(updatedAt = "2026-11-01T10:00:00+00:00").isEdited)
    }

    @Test
    fun timestampsAreComparedAcrossOffsetsAndFormats() {
        val created = "2026-11-01T10:00:00.123456+00:00"

        assertTrue(note(createdAt = created, updatedAt = "2026-11-01T11:00:02+01:00").isEdited)
        assertFalse(note(createdAt = created, updatedAt = "2026-11-01T11:00:00.5+01:00").isEdited)
        assertTrue(note(createdAt = created, updatedAt = "2026-11-01T10:00:05Z").isEdited)
    }

    @Test
    fun anUpdateBeforeCreationIsNotEdited() {
        assertFalse(note(updatedAt = "2026-11-01T09:00:00+00:00").isEdited)
    }

    @Test
    fun missingOrUnreadableTimestampsAreNotEdited() {
        assertFalse(note(updatedAt = null).isEdited)
        assertFalse(note(updatedAt = "yesterday").isEdited)
        assertFalse(note(createdAt = "", updatedAt = "2026-11-01T10:00:09+00:00").isEdited)
    }
}
