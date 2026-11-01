package com.mowtiie.writa.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object KeystoreKeys {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_SIZE_BITS = 256

    /** The non-exportable AES-256-GCM key stored under [alias], created on first use. */
    @Throws(GeneralSecurityException::class)
    fun getOrCreate(alias: String): SecretKey =
        try {
            val keyStore = loadKeyStore()
            val existing = (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
            existing ?: generate(alias)
        } catch (e: IOException) {
            throw GeneralSecurityException(e)
        } catch (e: ProviderException) {
            throw GeneralSecurityException(e)
        }

    fun delete(alias: String) {
        try {
            loadKeyStore().deleteEntry(alias)
        } catch (_: GeneralSecurityException) {
            // Nothing more can be done; the next open generates a new key or fails again.
        } catch (_: IOException) {
            // Same as above.
        }
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun generate(alias: String): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }
}
