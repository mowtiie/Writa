package com.mowtiie.writa.core.security

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class KeystoreNoteCryptoTest {
    private val alias = "test_note_key_${UUID.randomUUID()}"
    private val crypto = KeystoreNoteCrypto(alias)

    @After
    fun cleanUp() = KeystoreKeys.delete(alias)

    @Test
    fun decryptReturnsWhatWasEncrypted() {
        val sealed = crypto.encrypt(5, """{"title":"T é✓"}""")

        assertEquals("""{"title":"T é✓"}""", crypto.decrypt(5, sealed))
    }

    @Test
    fun theCiphertextDoesNotContainThePlainText() {
        assertFalse(crypto.encrypt(5, "very-private-note").contains("private"))
    }

    @Test
    fun aCiphertextIsBoundToItsRowId() {
        val sealed = crypto.encrypt(5, "text")

        assertNull(crypto.decrypt(6, sealed))
        assertNull(crypto.decrypt(-5, sealed))
        assertEquals("text", crypto.decrypt(5, sealed))
    }

    @Test
    fun everyEncryptionOfTheSameTextDiffers() {
        assertFalse(crypto.encrypt(5, "text") == crypto.encrypt(5, "text"))
    }

    @Test
    fun aLostKeyReadsAsUnreadableNotAsAnError() {
        val sealed = crypto.encrypt(5, "text")
        KeystoreKeys.delete(alias)

        assertNull(crypto.decrypt(5, sealed))
    }

    @Test
    fun garbageReadsAsUnreadable() {
        assertNull(crypto.decrypt(5, "not a sealed value"))
        assertNull(crypto.decrypt(5, ""))
    }
}
