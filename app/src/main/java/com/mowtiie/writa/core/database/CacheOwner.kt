package com.mowtiie.writa.core.database

import java.security.MessageDigest

/**
 * Names the account on a server that the cached notes belong to. If it changes, the cache is wiped before anything
 * is read or written, so notes never carry over to another account or server.
 */
object CacheOwner {
    fun of(
        baseUrl: String,
        email: String,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$baseUrl$SEPARATOR$email".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private const val SEPARATOR = '\n'
}
