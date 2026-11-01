package com.mowtiie.writa.core.security

import java.security.GeneralSecurityException
import java.util.Base64

/** Turns a named string value into text that is safe to keep in preferences, bound to its name. */
class ValueSealer(
    private val aesGcm: AesGcm,
) {
    @Throws(GeneralSecurityException::class)
    fun seal(
        name: String,
        value: String,
    ): String {
        val blob = aesGcm.encrypt(name.toByteArray(Charsets.UTF_8), value.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(blob)
    }

    /** Null when the text is malformed, was sealed for another name, or the key no longer fits. */
    fun unseal(
        name: String,
        sealed: String,
    ): String? =
        try {
            val blob = Base64.getDecoder().decode(sealed)
            String(aesGcm.decrypt(name.toByteArray(Charsets.UTF_8), blob), Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
}
