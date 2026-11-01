package com.mowtiie.writa.core.security

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM with a fresh random IV per message, stored as `iv || ciphertext`. The associated data is
 * authenticated but not stored, so a ciphertext only opens for the context it was sealed for.
 */
class AesGcm(
    private val key: () -> SecretKey,
) {
    @Throws(GeneralSecurityException::class)
    fun encrypt(
        associatedData: ByteArray,
        plaintext: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(associatedData)
        val iv = cipher.iv
        if (iv.size != IV_LENGTH_BYTES) throw GeneralSecurityException("Unexpected IV length")
        return iv + cipher.doFinal(plaintext)
    }

    @Throws(GeneralSecurityException::class)
    fun decrypt(
        associatedData: ByteArray,
        blob: ByteArray,
    ): ByteArray {
        if (blob.size <= IV_LENGTH_BYTES) throw GeneralSecurityException("Ciphertext too short")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_LENGTH_BITS, blob, 0, IV_LENGTH_BYTES))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(blob, IV_LENGTH_BYTES, blob.size - IV_LENGTH_BYTES)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val TAG_LENGTH_BITS = 128
    }
}
