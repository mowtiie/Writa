package com.mowtiie.writa.feature.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorModelsTest {
    @Test
    fun aCommaEndsEachTagSoSeveralCanBePastedAtOnce() {
        val commit = TagEntry.commit(emptyList(), "home, #work ,  ,Errands")

        assertEquals(listOf("home", "work", "Errands"), commit.tags)
        assertNull(commit.rejected)
    }

    @Test
    fun aTagTheNoteAlreadyHasIsDroppedWhateverItsCase() {
        assertEquals(listOf("Home"), TagEntry.commit(listOf("Home"), "home").tags)
    }

    @Test
    fun theFirstTagThatDoesNotFitIsKeptAndTheOthersAreStillAdded() {
        val full = (1..9).map { "t$it" }

        val commit = TagEntry.commit(full, "ten, eleven, twelve")

        assertEquals(full + "ten", commit.tags)
        assertEquals("eleven", commit.rejected)
    }

    @Test
    fun suggestionsSkipTagsTheNoteHasAndMatchAnywhereInTheTag() {
        val known = listOf("Home", "Work", "Homework")

        assertEquals(listOf("Work", "Homework"), TagEntry.suggestions(known, listOf("Home"), "work"))
        assertEquals(listOf("Work", "Homework"), TagEntry.suggestions(known, listOf("Home"), ""))
    }

    @Test
    fun noMoreThanEightSuggestionsAreOffered() {
        val known = (1..20).map { "tag$it" }

        assertEquals(8, TagEntry.suggestions(known, emptyList(), "tag").size)
    }

    @Test
    fun draftsAreTheSameIgnoringSpacesAndTheCaseAndOrderOfTags() {
        val a = NoteDraft(" Title ", "body ", listOf("a", "B"), true)
        val b = NoteDraft("Title", " body", listOf("b", "A"), true)

        assertTrue(a.sameAs(b))
        assertFalse(a.sameAs(b.copy(pinned = false)))
        assertFalse(a.sameAs(b.copy(content = "other")))
    }

    @Test
    fun theTagsToSaveIncludeWhatIsTypedButNotYetAdded() {
        val state = EditorUiState(draft = NoteDraft(tags = listOf("a")), tagInput = "b, c")

        assertEquals(listOf("a", "b", "c"), state.tagsToSave)
    }
}
