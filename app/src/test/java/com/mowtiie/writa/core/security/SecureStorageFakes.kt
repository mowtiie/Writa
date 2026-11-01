package com.mowtiie.writa.core.security

import java.security.GeneralSecurityException

class FakeSecureStorage(
    val entries: MutableMap<String, String> = mutableMapOf(),
    var failOnWrite: Boolean = false,
) : SecureStorage {
    override fun get(name: String): String? = entries[name]

    override fun putAll(values: Map<String, String?>) {
        if (failOnWrite) throw GeneralSecurityException("cannot encrypt")
        for ((name, value) in values) {
            if (value == null) entries.remove(name) else entries[name] = value
        }
    }

    override fun clear() = entries.clear()
}

class FakeSecureStorageFactory(
    private val openResults: MutableList<SecureStorage?> = mutableListOf(FakeSecureStorage()),
) : SecureStorageFactory {
    var openCalls = 0
    var destroyCalls = 0

    override fun open(): SecureStorage {
        openCalls++
        return openResults.removeAt(0) ?: throw GeneralSecurityException("keystore unusable")
    }

    override fun destroy() {
        destroyCalls++
    }
}
