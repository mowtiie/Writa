package com.mowtiie.writa.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import javax.crypto.KeyGenerator

class ValueSealerTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val sealer = ValueSealer(AesGcm { key })

    @Test
    fun unsealReturnsTheSealedValue() {
        val sealed = sealer.seal("access_token", "tokén ✓")

        assertEquals("tokén ✓", sealer.unseal("access_token", sealed))
    }

    @Test
    fun aSealedValueDoesNotOpenUnderAnotherName() {
        val sealed = sealer.seal("access_token", "abc")

        assertNull(sealer.unseal("refresh_token", sealed))
    }

    @Test
    fun malformedTextReadsAsAbsent() {
        assertNull(sealer.unseal("access_token", "not base64 !!"))
        assertNull(sealer.unseal("access_token", ""))
        assertNull(sealer.unseal("access_token", "AAAA"))
    }
}
