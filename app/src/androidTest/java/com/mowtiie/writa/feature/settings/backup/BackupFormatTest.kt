package com.mowtiie.writa.feature.settings.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.Instant

class BackupFormatTest {
    private val format = BackupFormat()
    private val exportedAt = Instant.parse("2026-11-05T12:30:09Z")

    private fun write(vararg notes: BackupNote): String {
        val out = ByteArrayOutputStream()
        format.write(out, notes.toList(), exportedAt)
        return out.toString(Charsets.UTF_8)
    }

    private fun read(json: String): List<BackupNote> =
        format.read(ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)))

    private fun note(
        title: String = "t",
        content: String = "c",
        createdAt: String? = null,
        tags: List<String> = emptyList(),
        pinned: Boolean = false,
        archived: Boolean = false,
    ) = BackupNote(title, content, createdAt, tags, pinned, archived)

    private fun root(text: String) = Json.parseToJsonElement(text).jsonObject

    // ---------------------------- writing ----------------------------

    @Test
    fun theFileHasTheDocumentedStructureAndNoIds() {
        val file = root(write(note("T", "C", createdAt = "2026-01-01T00:00:00Z")))

        assertEquals(
            1,
            file
                .getValue("version")
                .jsonPrimitive.content
                .toInt(),
        )
        assertEquals(
            exportedAt.toEpochMilli(),
            file
                .getValue("exportedAt")
                .jsonPrimitive.content
                .toLong(),
        )
        val entry =
            file
                .getValue("notes")
                .jsonArray
                .single()
                .jsonObject
        assertEquals(setOf("title", "content", "createdAt"), entry.keys)
        assertEquals("T", entry.getValue("title").jsonPrimitive.content)
        assertFalse("id" in entry)
    }

    @Test
    fun optionalFieldsAreWrittenOnlyWhenSet() {
        val plain =
            root(write(note()))
                .getValue("notes")
                .jsonArray
                .single()
                .jsonObject
        val full =
            root(write(note(tags = listOf("a", "b"), pinned = true, archived = true)))
                .getValue("notes")
                .jsonArray
                .single()
                .jsonObject

        assertEquals(setOf("title", "content"), plain.keys)
        assertEquals(setOf("title", "content", "pinned", "archived", "tags"), full.keys)
        assertEquals(listOf("a", "b"), full.getValue("tags").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun anEmptyBackupIsStillAValidFile() {
        assertEquals(JsonArray(emptyList()), root(write()).getValue("notes"))
        assertEquals(emptyList<BackupNote>(), read(write()))
    }

    @Test
    fun writingLeavesTheStreamOpenForTheCaller() {
        val out = ClosingSpy()

        format.write(out, listOf(note()), exportedAt)

        assertFalse(out.closed)
        assertTrue(out.size() > 0)
    }

    // ---------------------------- the round trip ----------------------------

    @Test
    fun exportedNotesComeBackExactly() {
        val notes =
            listOf(
                note("First", "line one\nline two", createdAt = "2026-01-01T00:00:00Z"),
                note("Unicode é中文 😀", "quote \" backslash \\ tab \t slash /"),
                note("Empty content", ""),
                note("Flags", "x", tags = listOf("Work", "Home Office"), pinned = true, archived = true),
                note("Control", "bell \u0007 separator   end"),
            )

        assertEquals(notes, read(write(*notes.toTypedArray())))
    }

    @Test
    fun aHugeBackupStreamsThrough() {
        val big = "x".repeat(5_000_000)
        val notes = List(2_000) { note("Note $it", "body $it") } + note("Big", big)

        val back = read(write(*notes.toTypedArray()))

        assertEquals(2_001, back.size)
        assertEquals("Note 1999", back[1999].title)
        assertEquals(big, back.last().content)
    }

    @Test
    fun readingLeavesTheStreamOpenForTheCaller() {
        val input = ClosingInput(write(note()).toByteArray())

        format.read(input)

        assertFalse(input.closed)
    }

    // ---------------------------- reading what other files hold ----------------------------

    @Test
    fun theKeysMayComeInAnyOrder() {
        val reordered =
            """{"notes":[{"content":"c","title":"t","tags":["a"],"pinned":true}],"exportedAt":1,"version":1}"""

        assertEquals(listOf(note("t", "c", tags = listOf("a"), pinned = true)), read(reordered))
    }

    @Test
    fun aNewerVersionAfterTheNotesIsStillRejected() {
        val error =
            assertThrows(UnsupportedBackupVersionException::class.java) {
                read("""{"notes":[{"title":"t"}],"version":2}""")
            }

        assertEquals(2, error.version)
    }

    @Test
    fun aNewerVersionBeforeTheNotesIsRejectedToo() {
        assertThrows(UnsupportedBackupVersionException::class.java) { read("""{"version":99,"notes":[]}""") }
    }

    @Test
    fun aByteOrderMarkAndLeadingSpacesAreSkipped() {
        val file = "﻿  \n" + write(note("t"))

        assertEquals(listOf("t"), read(file).map { it.title })
    }

    @Test
    fun aFileWithoutAVersionIsAccepted() {
        assertEquals(1, read("""{"notes":[{"title":"t"}]}""").size)
    }

    @Test
    fun entriesWithoutAUsableTitleAreSkipped() {
        val json =
            """{"version":1,"notes":[
                {"content":"no title"},
                {"title":null},
                {"title":42},
                {"title":true},
                {"title":"  "},
                {"title":"keep","content":"c"}
            ]}"""

        assertEquals(listOf("keep"), read(json).map { it.title })
    }

    @Test
    fun unknownFieldsAndNestedValuesAreIgnored() {
        val json =
            """{"version":1,"future":{"a":[1,2,{"b":null}]},"notes":[
                {"title":"t","content":"c","extra":{"deep":{"deeper":[1,2,3]}},"list":[[],[{}]],"createdAt":5}
            ]}"""

        assertEquals(listOf(note("t", "c")), read(json))
    }

    @Test
    fun entriesThatAreNotObjectsAreSkipped() {
        assertEquals(listOf("keep"), read("""{"notes":["text",7,null,[1],{"title":"keep"},true]}""").map { it.title })
    }

    @Test
    fun valuesOfTheWrongTypeAreIgnored() {
        val json =
            """{"notes":[{"title":"t","content":5,"pinned":"yes","archived":1,"tags":"work","createdAt":false}]}"""

        assertEquals(listOf(note("t", "")), read(json))
    }

    @Test
    fun onlyTextTagsAreKeptAndTheyAreCleanedUp() {
        val json = """{"notes":[{"title":"t","tags":[1," #Work ","work",null,{"x":1},"Home",true]}]}"""

        assertEquals(listOf("Work", "Home"), read(json).single().tags)
    }

    @Test
    fun notesFromSeveralArraysAreAllKept() {
        assertEquals(listOf("a", "b"), read("""{"notes":[{"title":"a"}],"notes":[{"title":"b"}]}""").map { it.title })
    }

    // ---------------------------- files that are not backups ----------------------------

    @Test
    fun aFileWithoutAUsableNotesArrayIsRejected() {
        for (json in listOf("""{"version":1}""", """{"notes":null}""", """{"notes":{}}""", """{"notes":"x"}""", "{}")) {
            assertThrows(json, InvalidBackupException::class.java) { read(json) }
        }
    }

    @Test
    fun filesThatAreNotJsonObjectsAreRejected() {
        val files =
            listOf("not json at all", "[1,2,3]", "", "null", "42", "\"text\"", "{", """{"notes":[{"title":"t"""")
        for (json in files) {
            assertThrows(json, InvalidBackupException::class.java) { read(json) }
        }
    }

    @Test
    fun aVersionThatIsNotANumberIsRejected() {
        val files =
            listOf(
                """{"version":"abc","notes":[]}""",
                """{"version":1.5,"notes":[]}""",
                """{"version":null,"notes":[]}""",
            )
        for (json in files) {
            assertThrows(json, InvalidBackupException::class.java) { read(json) }
        }
    }

    @Test
    fun aFileCutOffInTheMiddleIsRejected() {
        val whole = write(note("a", "x".repeat(1000)), note("b"))

        assertThrows(InvalidBackupException::class.java) { read(whole.substring(0, whole.length / 2)) }
    }

    private class ClosingSpy : ByteArrayOutputStream() {
        var closed = false

        override fun close() {
            closed = true
            super.close()
        }
    }

    private class ClosingInput(
        bytes: ByteArray,
    ) : InputStream() {
        private val inner = ByteArrayInputStream(bytes)
        var closed = false

        override fun read() = inner.read()

        override fun close() {
            closed = true
        }
    }
}
