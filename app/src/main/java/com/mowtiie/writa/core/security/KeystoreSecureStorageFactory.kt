package com.mowtiie.writa.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.security.GeneralSecurityException

class KeystoreSecureStorageFactory(
    private val context: Context,
    private val prefsName: String,
    private val keyAlias: String,
) : SecureStorageFactory {
    @Throws(GeneralSecurityException::class)
    override fun open(): SecureStorage {
        KeystoreKeys.getOrCreate(keyAlias)
        val sealer = ValueSealer(AesGcm { KeystoreKeys.getOrCreate(keyAlias) })
        return PreferencesSecureStorage(context.getSharedPreferences(prefsName, Context.MODE_PRIVATE), sealer)
    }

    override fun destroy() {
        context.deleteSharedPreferences(prefsName)
        KeystoreKeys.delete(keyAlias)
    }
}

internal class PreferencesSecureStorage(
    private val preferences: SharedPreferences,
    private val sealer: ValueSealer,
) : SecureStorage {
    override fun get(name: String): String? = preferences.getString(name, null)?.let { sealer.unseal(name, it) }

    @Throws(GeneralSecurityException::class)
    override fun putAll(values: Map<String, String?>) {
        val sealed = values.mapValues { (name, value) -> value?.let { sealer.seal(name, it) } }
        preferences.edit {
            for ((name, value) in sealed) {
                if (value == null) remove(name) else putString(name, value)
            }
        }
    }

    override fun clear() {
        preferences.edit { clear() }
    }
}
