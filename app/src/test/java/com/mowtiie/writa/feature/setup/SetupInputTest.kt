package com.mowtiie.writa.feature.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetupInputTest {
    @Test
    fun anHttpsUrlIsAccepted() {
        assertNull(SetupInput.urlProblem("https://abc.supabase.co"))
        assertNull(SetupInput.urlProblem("  https://abc.supabase.co/  "))
        assertNull(SetupInput.urlProblem("HTTPS://ABC.SUPABASE.CO"))
        assertNull(SetupInput.urlProblem("https://localhost:54321"))
        assertNull(SetupInput.urlProblem("https://my.host/supabase"))
    }

    @Test
    fun anEmptyUrlIsRequired() {
        assertEquals(UrlProblem.Required, SetupInput.urlProblem(""))
        assertEquals(UrlProblem.Required, SetupInput.urlProblem("   "))
        assertEquals(UrlProblem.Required, SetupInput.urlProblem("/"))
    }

    @Test
    fun onlyHttpsIsAllowed() {
        assertEquals(UrlProblem.SchemeRequired, SetupInput.urlProblem("http://abc.supabase.co"))
        assertEquals(UrlProblem.SchemeRequired, SetupInput.urlProblem("abc.supabase.co"))
        assertEquals(UrlProblem.SchemeRequired, SetupInput.urlProblem("ftp://abc.supabase.co"))
        assertEquals(UrlProblem.SchemeRequired, SetupInput.urlProblem("https:/abc.supabase.co"))
    }

    @Test
    fun anUrlThatCannotBeParsedIsInvalid() {
        assertEquals(UrlProblem.Invalid, SetupInput.urlProblem("https://"))
        assertEquals(UrlProblem.Invalid, SetupInput.urlProblem("https:// spaced.com"))
        assertEquals(UrlProblem.Invalid, SetupInput.urlProblem("https://exa mple.com"))
        assertEquals(UrlProblem.Invalid, SetupInput.urlProblem("https://[broken"))
    }

    @Test
    fun theUrlIsSavedTrimmedWithoutOneTrailingSlash() {
        assertEquals("https://abc.supabase.co", SetupInput.normalizeUrl("  https://abc.supabase.co/ \n"))
        assertEquals("https://abc.supabase.co/", SetupInput.normalizeUrl("https://abc.supabase.co//"))
        assertEquals("https://abc.supabase.co", SetupInput.normalizeUrl("https://abc.supabase.co"))
    }

    @Test
    fun aKeyOfVisibleAsciiIsAccepted() {
        assertNull(SetupInput.keyProblem("sb_publishable_AbC123-_."))
        assertNull(SetupInput.keyProblem("eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiYW5vbiJ9.sig"))
        assertNull(SetupInput.keyProblem("  padded-key \n"))
    }

    @Test
    fun anEmptyKeyIsRequired() {
        assertEquals(KeyProblem.Required, SetupInput.keyProblem(""))
        assertEquals(KeyProblem.Required, SetupInput.keyProblem("  \n "))
    }

    @Test
    fun aKeyWithSpacesOrNonAsciiIsInvalidBecauseItCouldNotBeSentAsAHeader() {
        assertEquals(KeyProblem.Invalid, SetupInput.keyProblem("two words"))
        assertEquals(KeyProblem.Invalid, SetupInput.keyProblem("new\nline"))
        assertEquals(KeyProblem.Invalid, SetupInput.keyProblem("café"))
        assertEquals(KeyProblem.Invalid, SetupInput.keyProblem("emoji😀"))
        assertEquals(KeyProblem.Invalid, SetupInput.keyProblem("tab\tinside"))
    }

    @Test
    fun theKeyIsSavedTrimmed() {
        assertEquals("key", SetupInput.normalizeKey("  key\n"))
    }
}
