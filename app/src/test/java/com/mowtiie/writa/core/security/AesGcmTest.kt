package com.mowtiie.writa.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.GeneralSecurityException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class AesGcmTest {
    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val key = newKey()
    private val aesGcm = AesGcm { key }
    private val aad = "name".toByteArray()
    private val plaintext = "secret".toByteArray()

    @Test
    fun decryptReturnsWhatWasEncrypted() {
        val blob = aesGcm.encrypt(aad, plaintext)

        assertArrayEquals(plaintext, aesGcm.decrypt(aad, blob))
    }

    @Test
    fun everyEncryptionUsesAFreshIv() {
        val first = aesGcm.encrypt(aad, plaintext)
        val second = aesGcm.encrypt(aad, plaintext)

        assertFalse(first.contentEquals(second))
    }

    @Test
    fun differentAssociatedDataIsRejected() {
        val blob = aesGcm.encrypt(aad, plaintext)

        assertThrows(GeneralSecurityException::class.java) { aesGcm.decrypt("other".toByteArray(), blob) }
    }

    @Test
    fun aDifferentKeyIsRejected() {
        val blob = aesGcm.encrypt(aad, plaintext)
        val other = newKey()

        assertThrows(GeneralSecurityException::class.java) { AesGcm { other }.decrypt(aad, blob) }
    }

    @Test
    fun aTamperedCiphertextIsRejected() {
        val blob = aesGcm.encrypt(aad, plaintext)
        blob[blob.lastIndex] = (blob.last().toInt() xor 1).toByte()

        assertThrows(GeneralSecurityException::class.java) { aesGcm.decrypt(aad, blob) }
    }

    @Test
    fun aTooShortBlobIsRejected() {
        assertThrows(GeneralSecurityException::class.java) { aesGcm.decrypt(aad, ByteArray(12)) }
        assertThrows(GeneralSecurityException::class.java) { aesGcm.decrypt(aad, ByteArray(0)) }
    }
}
