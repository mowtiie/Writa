package com.mowtiie.writa.feature.settings.backup

import com.mowtiie.writa.feature.notes.Note
import com.mowtiie.writa.feature.notes.Tags
import com.mowtiie.writa.feature.notes.remote.NoteInsert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRulesTest {
    private fun raw(
        title: String? = "t",
        content: String? = "c",
        createdAt: String? = null,
        pinned: Boolean? = null,
        archived: Boolean? = null,
        tags: List<String>? = null,
    ) = RawBackupNote(title, content, createdAt, pinned, archived, tags)

    private fun interpret(
        vararg notes: RawBackupNote,
        version: Int? = 1,
    ) = BackupRules.interpret(RawBackup(version, notes.toList()))

    // ---------------------------- reading ----------------------------

    @Test
    fun aCompleteEntryIsAccepted() {
        val note =
            interpret(
                raw(
                    "Title",
                    "Body",
                    createdAt = "2026-01-01T00:00:00Z",
                    pinned = true,
                    archived = true,
                    tags = listOf("a"),
                ),
            ).single()

        assertEquals(BackupNote("Title", "Body", "2026-01-01T00:00:00Z", listOf("a"), true, true), note)
    }

    @Test
    fun missingOptionalFieldsTakeTheirDefaults() {
        val note = interpret(raw(title = "t", content = null)).single()

        assertEquals(BackupNote("t", "", null, emptyList(), pinned = false, archived = false), note)
    }

    @Test
    fun entriesWithoutAUsableTitleAreSkipped() {
        val notes = interpret(raw(title = null), raw(title = ""), raw(title = "   "), raw(title = "keep"))

        assertEquals(listOf("keep"), notes.map { it.title })
    }

    @Test
    fun theTitleIsTrimmedButTheContentIsKeptAsItIs() {
        val note = interpret(raw(title = "  Title \n", content = "  body\n")).single()

        assertEquals("Title", note.title)
        assertEquals("  body\n", note.content)
    }

    @Test
    fun tagsAreCleanedLikeEverywhereElse() {
        val note = interpret(raw(tags = listOf(" #Work ", "work", "", "Home"))).single()

        assertEquals(listOf("Work", "Home"), note.tags)
    }

    @Test
    fun moreTagsThanANoteMayHaveAreCutOff() {
        val note = interpret(raw(tags = List(25) { "tag$it" })).single()

        assertEquals(Tags.MAX_TAGS_PER_NOTE, note.tags.size)
    }

    @Test
    fun theOrderOfTheEntriesIsKept() {
        assertEquals(listOf("b", "a", "c"), interpret(raw("b"), raw("a"), raw("c")).map { it.title })
    }

    @Test
    fun anEmptyNotesArrayIsAValidBackupOfNothing() {
        assertTrue(interpret().isEmpty())
    }

    @Test
    fun aFileWithoutAVersionIsAccepted() {
        assertEquals(1, interpret(raw(), version = null).size)
    }

    @Test
    fun theCurrentAndOlderVersionsAreAccepted() {
        assertEquals(1, interpret(raw(), version = 1).size)
        assertEquals(1, interpret(raw(), version = 0).size)
    }

    @Test
    fun aNewerVersionIsRejectedWithItsNumber() {
        val error = assertThrows(UnsupportedBackupVersionException::class.java) { interpret(raw(), version = 2) }

        assertEquals(2, error.version)
        assertTrue(error.message.orEmpty().contains("newer"))
    }

    @Test
    fun aFileWithoutANotesArrayIsRejected() {
        assertThrows(InvalidBackupException::class.java) { BackupRules.interpret(RawBackup(version = 1, notes = null)) }
    }

    @Test
    fun aNewerVersionWinsOverAMissingNotesArray() {
        assertThrows(UnsupportedBackupVersionException::class.java) {
            BackupRules.interpret(RawBackup(version = 9, notes = null))
        }
    }

    // ---------------------------- exporting ----------------------------

    private fun note(
        id: Long,
        title: String = "t$id",
        archived: Boolean = false,
        deletedAt: String? = null,
    ) = Note(id, title, "c$id", listOf("a"), true, archived, "2026-01-01T00:00:00Z", null, deletedAt)

    @Test
    fun trashedNotesAreNotExportedButArchivedOnesAre() {
        val selected =
            BackupRules.selectForExport(
                listOf(note(1), note(2, archived = true), note(3, deletedAt = "2026-02-01T00:00:00Z")),
            )

        assertEquals(listOf(1L, 2L), selected.map { it.id })
    }

    @Test
    fun exportingKeepsTheOrder() {
        assertEquals(listOf(3L, 1L, 2L), BackupRules.selectForExport(listOf(note(3), note(1), note(2))).map { it.id })
    }

    @Test
    fun aNoteIsExportedWithoutItsIdOrSyncState() {
        val exported = BackupRules.toBackupNote(note(7, title = "T"))

        assertEquals(
            BackupNote("T", "c7", "2026-01-01T00:00:00Z", listOf("a"), pinned = true, archived = false),
            exported,
        )
    }

    @Test
    fun aNoteWithoutContentOrCreationTimeIsExportedWithEmptyContentAndNoTime() {
        val bare = note(1).copy(content = null, createdAt = "")

        val exported = BackupRules.toBackupNote(bare)

        assertEquals("", exported.content)
        assertEquals(null, exported.createdAt)
    }

    // ---------------------------- importing ----------------------------

    @Test
    fun importingMakesNewNotesAndIgnoresTheCreationTime() {
        val insert =
            BackupRules.toInsert(
                BackupNote(
                    "T",
                    "C",
                    createdAt = "2020-01-01T00:00:00Z",
                    tags = listOf("a"),
                    pinned = true,
                    archived = true,
                ),
            )

        assertEquals(NoteInsert("T", "C", listOf("a"), pinned = true, archived = true), insert)
        assertEquals(null, insert.clientId)
        assertEquals(null, insert.deletedAt)
    }

    @Test
    fun aRoundTripOfTheRulesKeepsTheNote() {
        val original = note(1, title = "Round trip")

        val back = interpret(rawOf(BackupRules.toBackupNote(original))).single()

        assertEquals(original.title, back.title)
        assertEquals(original.content, back.content)
        assertEquals(original.tags, back.tags)
        assertEquals(original.pinned, back.pinned)
        assertFalse(back.archived)
    }

    private fun rawOf(note: BackupNote) =
        RawBackupNote(note.title, note.content, note.createdAt, note.pinned, note.archived, note.tags)
}
