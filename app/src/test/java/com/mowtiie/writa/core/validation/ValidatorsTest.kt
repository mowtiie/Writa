package com.mowtiie.writa.core.validation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {
    @Test
    fun acceptsOrdinaryAddresses() {
        assertTrue(Validators.isPlausibleEmail("tester@example.com"))
        assertTrue(Validators.isPlausibleEmail("first.last+tag@sub.example.co.uk"))
        assertTrue(Validators.isPlausibleEmail("  padded@example.com  "))
    }

    @Test
    fun acceptsHostsWithoutADotForSelfHostedSetups() {
        assertTrue(Validators.isPlausibleEmail("me@localhost"))
    }

    @Test
    fun rejectsObviousTypos() {
        assertFalse(Validators.isPlausibleEmail(null))
        assertFalse(Validators.isPlausibleEmail(""))
        assertFalse(Validators.isPlausibleEmail("   "))
        assertFalse(Validators.isPlausibleEmail("no-at-sign.example.com"))
        assertFalse(Validators.isPlausibleEmail("@example.com"))
        assertFalse(Validators.isPlausibleEmail("name@"))
        assertFalse(Validators.isPlausibleEmail("a@b@example.com"))
        assertFalse(Validators.isPlausibleEmail("with space@example.com"))
        assertFalse(Validators.isPlausibleEmail("tester%40example.com"))
    }
}
