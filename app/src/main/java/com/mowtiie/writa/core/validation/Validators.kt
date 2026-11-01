package com.mowtiie.writa.core.validation

object Validators {
    /**
     * Lenient on purpose: no dot is required in the host, because self-hosted setups may use addresses like
     * `me@localhost`. The server stays the authority.
     */
    fun isPlausibleEmail(value: String?): Boolean {
        val email = value?.trim() ?: return false
        val at = email.indexOf('@')
        val hasSingleInnerAt = at > 0 && at == email.lastIndexOf('@') && at < email.length - 1
        return hasSingleInnerAt && email.none { it.isWhitespace() }
    }
}
