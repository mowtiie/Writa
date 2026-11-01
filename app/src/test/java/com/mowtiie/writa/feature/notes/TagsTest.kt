package com.mowtiie.writa.feature.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagsTest {
    @Test
    fun cleanTrimsCollapsesAndDropsHash() {
        assertEquals("home office", Tags.clean("  home    office "))
        assertEquals("work", Tags.clean("#work"))
        assertEquals("work", Tags.clean("##  work"))
        assertEquals("work", Tags.clean("# # work"))
        assertEquals("", Tags.clean("   "))
        assertEquals("", Tags.clean("#"))
        assertEquals("", Tags.clean(null))
    }

    @Test
    fun cleanShortensLongTags() {
        assertEquals(Tags.MAX_TAG_LENGTH, Tags.clean("x".repeat(Tags.MAX_TAG_LENGTH + 20)).length)
        assertEquals("a".repeat(29), Tags.clean("a".repeat(29) + " " + "b".repeat(10)))
    }

    @Test
    fun addIgnoresDuplicatesIgnoringCase() {
        val tags = Tags.add(emptyList(), "Work")

        assertEquals(listOf("Work"), tags)
        assertEquals(listOf("Work"), Tags.add(tags, " work "))
        assertEquals(listOf("Work"), Tags.add(tags, ""))
    }

    @Test
    fun addStopsAtTheLimit() {
        val full = (0 until Tags.MAX_TAGS_PER_NOTE).fold(emptyList<String>()) { tags, i -> Tags.add(tags, "t$i") }

        assertEquals(Tags.MAX_TAGS_PER_NOTE, full.size)
        assertEquals(full, Tags.add(full, "one more"))
    }

    @Test
    fun normalizeKeepsTheFirstSpellingAndDropsJunk() {
        val result = Tags.normalize(listOf("Work", " work", "#Home", "", "  ", "HOME"))

        assertEquals(listOf("Work", "Home"), result)
    }

    @Test
    fun normalizeAcceptsNull() {
        assertTrue(Tags.normalize(null).isEmpty())
    }

    @Test
    fun normalizeDropsEverythingOverThePerNoteLimit() {
        val result = Tags.normalize((0 until Tags.MAX_TAGS_PER_NOTE + 5).map { "t$it" })

        assertEquals(Tags.MAX_TAGS_PER_NOTE, result.size)
    }

    @Test
    fun sameTagsIgnoresOrderAndCase() {
        assertTrue(Tags.sameTags(listOf("a", "B"), listOf("b", "A")))
        assertFalse(Tags.sameTags(listOf("a"), listOf("a", "b")))
        assertFalse(Tags.sameTags(listOf("a", "b"), listOf("a", "c")))
        assertTrue(Tags.sameTags(emptyList(), emptyList()))
    }

    @Test
    fun matchesAnyOfTheSelectedTags() {
        val selected = setOf("home", "work")

        assertTrue(Tags.matchesAny(listOf("Home"), selected))
        assertTrue(Tags.matchesAny(listOf("x", "WORK"), selected))
        assertFalse(Tags.matchesAny(listOf("other"), selected))
        assertFalse(Tags.matchesAny(emptyList(), selected))
    }

    @Test
    fun emptySelectionMatchesEverything() {
        assertTrue(Tags.matchesAny(emptyList(), emptySet()))
        assertTrue(Tags.matchesAny(listOf("a"), emptySet()))
    }

    @Test
    fun usageCountsCaseInsensitivelyMostUsedFirst() {
        val usage =
            Tags.usage(
                listOf(
                    listOf("Work", "home"),
                    listOf("work", "alpha"),
                    listOf("WORK"),
                    listOf("zeta"),
                ),
            )

        assertEquals(listOf("Work", "alpha", "home", "zeta"), usage.keys.toList())
        assertEquals(3, usage["Work"])
        assertEquals(1, usage["home"])
    }
}
