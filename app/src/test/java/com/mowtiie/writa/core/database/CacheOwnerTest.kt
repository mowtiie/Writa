package com.mowtiie.writa.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheOwnerTest {
    @Test
    fun theSameAccountOnTheSameServerAlwaysGivesTheSameOwner() {
        assertEquals(
            CacheOwner.of("https://a.supabase.co", "me@example.com"),
            CacheOwner.of("https://a.supabase.co", "me@example.com"),
        )
    }

    @Test
    fun aDifferentAccountGivesADifferentOwner() {
        assertNotEquals(
            CacheOwner.of("https://a.supabase.co", "me@example.com"),
            CacheOwner.of("https://a.supabase.co", "you@example.com"),
        )
    }

    @Test
    fun aDifferentServerGivesADifferentOwner() {
        assertNotEquals(
            CacheOwner.of("https://a.supabase.co", "me@example.com"),
            CacheOwner.of("https://b.supabase.co", "me@example.com"),
        )
    }

    @Test
    fun valuesThatOnlyDifferInWhereTheyAreSplitAreDifferentOwners() {
        assertNotEquals(CacheOwner.of("https://a.co/x", "y@z"), CacheOwner.of("https://a.co/", "xy@z"))
    }

    @Test
    fun theOwnerIsASha256Hex() {
        val owner = CacheOwner.of("https://a.supabase.co", "me@example.com")

        assertEquals(64, owner.length)
        assertTrue(owner.all { it in "0123456789abcdef" })
    }

    @Test
    fun theOwnerDoesNotContainTheEmailOrUrl() {
        val owner = CacheOwner.of("https://a.supabase.co", "me@example.com")

        assertFalse(owner.contains("example") || owner.contains("supabase"))
    }
}
