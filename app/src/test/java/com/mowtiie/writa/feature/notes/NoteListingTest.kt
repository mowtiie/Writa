package com.mowtiie.writa.feature.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteListingTest {
    private fun note(
        id: Long,
        title: String = "t$id",
        content: String? = null,
        tags: List<String> = emptyList(),
        pinned: Boolean = false,
    ) = Note(id, title, content, tags, pinned, false, "c", null, null)

    private fun ids(
        notes: List<Note>,
        query: NoteListQuery = NoteListQuery(),
    ) = SearchableNotes(notes).apply(query).map { it.id }

    // The cache hands notes over newest first.
    private val newestFirst = listOf(note(3, "banana"), note(2, "Apple"), note(1, "cherry"))

    @Test
    fun newestKeepsTheOrderTheyCameIn() {
        assertEquals(listOf(3L, 2L, 1L), ids(newestFirst))
    }

    @Test
    fun oldestReversesIt() {
        assertEquals(listOf(1L, 2L, 3L), ids(newestFirst, NoteListQuery(sort = Sort.OLDEST)))
    }

    @Test
    fun titlesSortIgnoringCase() {
        assertEquals(listOf(2L, 3L, 1L), ids(newestFirst, NoteListQuery(sort = Sort.TITLE_AZ)))
        assertEquals(listOf(1L, 3L, 2L), ids(newestFirst, NoteListQuery(sort = Sort.TITLE_ZA)))
    }

    @Test
    fun equalTitlesKeepTheirOrder() {
        val same = listOf(note(3, "x"), note(2, "X"), note(1, "x"))

        assertEquals(listOf(3L, 2L, 1L), ids(same, NoteListQuery(sort = Sort.TITLE_AZ)))
    }

    @Test
    fun pinnedNotesComeFirstWhateverTheSort() {
        val notes = listOf(note(3, "c"), note(2, "a", pinned = true), note(1, "b", pinned = true))

        assertEquals(listOf(2L, 1L, 3L), ids(notes, NoteListQuery(sort = Sort.TITLE_AZ)))
        assertEquals(listOf(2L, 1L, 3L), ids(notes, NoteListQuery(sort = Sort.NEWEST)))
        assertEquals(listOf(1L, 2L, 3L), ids(notes, NoteListQuery(sort = Sort.OLDEST)))
    }

    @Test
    fun searchMatchesTitleContentAndTagsIgnoringCase() {
        val notes =
            listOf(
                note(1, title = "Shopping"),
                note(2, title = "x", content = "Buy MILK today"),
                note(3, title = "y", tags = listOf("Groceries")),
                note(4, title = "z"),
            )

        assertEquals(listOf(1L), ids(notes, NoteListQuery(search = "shop")))
        assertEquals(listOf(2L), ids(notes, NoteListQuery(search = "milk")))
        assertEquals(listOf(3L), ids(notes, NoteListQuery(search = "GROCER")))
    }

    @Test
    fun searchIgnoresSurroundingSpacesAndBlankMeansEverything() {
        assertEquals(listOf(3L), ids(newestFirst, NoteListQuery(search = "  banana ")))
        assertEquals(listOf(3L, 2L, 1L), ids(newestFirst, NoteListQuery(search = "   ")))
    }

    @Test
    fun searchWithNoMatchGivesAnEmptyList() {
        assertEquals(emptyList<Long>(), ids(newestFirst, NoteListQuery(search = "zzz")))
    }

    @Test
    fun aNoteWithoutContentStillMatchesOnItsTitle() {
        assertEquals(listOf(1L), ids(listOf(note(1, "hello", content = null)), NoteListQuery(search = "hello")))
    }

    @Test
    fun aTagFilterMatchesNotesWithAnyOfTheSelectedTags() {
        val notes =
            listOf(
                note(1, tags = listOf("Work")),
                note(2, tags = listOf("home", "x")),
                note(3, tags = listOf("other")),
                note(4),
            )

        assertEquals(listOf(1L, 2L), ids(notes, NoteListQuery(tagKeys = setOf("work", "home"))))
    }

    @Test
    fun searchAndTagsNarrowTheListTogether() {
        val notes =
            listOf(
                note(1, title = "meeting", tags = listOf("work")),
                note(2, title = "meeting", tags = listOf("home")),
                note(3, title = "lunch", tags = listOf("work")),
            )

        assertEquals(listOf(1L), ids(notes, NoteListQuery(search = "meet", tagKeys = setOf("work"))))
    }

    @Test
    fun anEmptyListStaysEmpty() {
        assertEquals(emptyList<Long>(), ids(emptyList(), NoteListQuery(search = "a", sort = Sort.TITLE_AZ)))
    }
}
