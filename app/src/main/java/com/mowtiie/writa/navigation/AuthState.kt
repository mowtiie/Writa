package com.mowtiie.writa.navigation

import com.mowtiie.writa.core.datastore.Connection
import com.mowtiie.writa.core.security.Session

/** Where the user stands: which screen the app belongs on. */
enum class AuthState {
    /** No server yet. */
    NotConfigured,

    /** A server, but nobody signed in. */
    SignedOut,

    SignedIn,
    ;

    /** The screen this state belongs on. */
    val route: Any
        get() =
            when (this) {
                NotConfigured -> SetupRoute
                SignedOut -> LoginRoute
                SignedIn -> NotesRoute
            }

    companion object {
        fun of(
            connection: Connection?,
            session: Session?,
        ): AuthState =
            when {
                connection == null -> NotConfigured
                session == null -> SignedOut
                else -> SignedIn
            }
    }
}
