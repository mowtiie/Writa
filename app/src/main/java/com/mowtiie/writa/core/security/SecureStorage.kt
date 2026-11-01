package com.mowtiie.writa.core.security

import java.security.GeneralSecurityException

interface SecureStorage {
    /** The stored value, or null if it is absent or can no longer be decrypted. */
    fun get(name: String): String?

    /** A null value removes that entry. */
    @Throws(GeneralSecurityException::class)
    fun putAll(values: Map<String, String?>)

    fun clear()
}

interface SecureStorageFactory {
    /** Slow on first use because of Keystore work, so call it off the main thread. */
    @Throws(GeneralSecurityException::class)
    fun open(): SecureStorage

    /** Deletes the key and the stored values so a broken store can be recreated from scratch. */
    fun destroy()
}
