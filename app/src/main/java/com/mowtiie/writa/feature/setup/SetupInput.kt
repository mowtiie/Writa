package com.mowtiie.writa.feature.setup

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed interface UrlProblem {
    data object Required : UrlProblem

    data object SchemeRequired : UrlProblem

    data object Invalid : UrlProblem

    data object NotReached : UrlProblem

    data object SecureConnection : UrlProblem

    data class UnexpectedResponse(
        val code: Int,
    ) : UrlProblem
}

sealed interface KeyProblem {
    data object Required : KeyProblem

    data object Invalid : KeyProblem

    data class Rejected(
        val code: Int,
    ) : KeyProblem
}

/** Checks of what the user typed, before the server is asked. The server stays the authority. */
object SetupInput {
    /** Visible ASCII only: anything else (spaces, newlines, unicode) would make OkHttp reject the header. */
    private val keyPattern = Regex("[\\x21-\\x7E]+")

    /** The URL as it is saved: trimmed and without one trailing slash. */
    fun normalizeUrl(raw: String): String = raw.trim().removeSuffix("/")

    fun normalizeKey(raw: String): String = raw.trim()

    fun urlProblem(raw: String): UrlProblem? {
        // Looked at before the trailing slash is dropped, which would otherwise eat the slash of `https://` itself.
        val typed = raw.trim()
        return when {
            normalizeUrl(typed).isEmpty() -> UrlProblem.Required
            !typed.startsWith("https://", ignoreCase = true) -> UrlProblem.SchemeRequired
            typed.toHttpUrlOrNull() == null -> UrlProblem.Invalid
            else -> null
        }
    }

    fun keyProblem(raw: String): KeyProblem? {
        val key = normalizeKey(raw)
        return when {
            key.isEmpty() -> KeyProblem.Required
            !keyPattern.matches(key) -> KeyProblem.Invalid
            else -> null
        }
    }
}
