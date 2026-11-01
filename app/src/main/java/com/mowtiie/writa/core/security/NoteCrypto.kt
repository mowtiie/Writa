package com.mowtiie.writa.core.security

import java.security.GeneralSecurityException

/**
 * Encrypts the text of cached notes. The row id is bound to the ciphertext as authenticated data, so a value
 * cannot be moved to another row and still decrypt.
 */
interface NoteCrypto {
    @Throws(GeneralSecurityException::class)
    fun encrypt(
        rowId: Long,
        plain: String,
    ): String

    /** The plain text, or null if the value cannot be decrypted (for example the key was lost). */
    fun decrypt(
        rowId: Long,
        stored: String,
    ): String?
}

class KeystoreNoteCrypto(
    keyAlias: String,
) : NoteCrypto {
    private val sealer = ValueSealer(AesGcm { KeystoreKeys.getOrCreate(keyAlias) })

    override fun encrypt(
        rowId: Long,
        plain: String,
    ): String = sealer.seal(entryName(rowId), plain)

    override fun decrypt(
        rowId: Long,
        stored: String,
    ): String? = sealer.unseal(entryName(rowId), stored)

    private fun entryName(rowId: Long) = "note:$rowId"
}
