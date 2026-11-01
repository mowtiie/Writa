package com.mowtiie.writa.feature.notes.remote

import com.mowtiie.writa.feature.notes.Note
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteNoteMappingTest {
    @Test
    fun aCompleteRowMapsFieldForField() {
        val remote =
            RemoteNote(
                id = 5_000_000_000L,
                title = "T",
                content = "C",
                tags = listOf("a"),
                pinned = true,
                archived = true,
                createdAt = "c",
                updatedAt = "u",
                deletedAt = "d",
                clientId = "ignored",
            )

        assertEquals(Note(5_000_000_000L, "T", "C", listOf("a"), true, true, "c", "u", "d"), remote.toNote())
    }

    @Test
    fun nullsFromTheServerBecomeSafeDefaults() {
        val note = RemoteNote(id = 1, title = "T").toNote()

        assertEquals(Note(1, "T", null, emptyList(), false, false, "", null, null), note)
    }
}
