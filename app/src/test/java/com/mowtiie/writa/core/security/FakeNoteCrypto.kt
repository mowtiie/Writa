package com.mowtiie.writa.core.security

import java.security.GeneralSecurityException

/** Reversible and bound to the row id, like the real thing, but readable in assertions. */
class FakeNoteCrypto : NoteCrypto {
    var failToEncrypt = false

    override fun encrypt(
        rowId: Long,
        plain: String,
    ): String {
        if (failToEncrypt) throw GeneralSecurityException("no key")
        return "enc:$rowId:$plain"
    }

    override fun decrypt(
        rowId: Long,
        stored: String,
    ): String? = stored.removePrefix("enc:$rowId:").takeIf { stored.startsWith("enc:$rowId:") }
}
