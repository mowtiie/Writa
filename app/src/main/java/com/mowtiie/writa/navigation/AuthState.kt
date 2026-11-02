package com.mowtiie.writa.navigation

import com.mowtiie.writa.core.datastore.Connection
import com.mowtiie.writa.core.security.Session
import kotlin.reflect.KClass

/** Where the user stands: which screen the app belongs on. */
enum class AuthState {
    /** No server yet. */
    NotConfigured,

    /** A server, but nobody signed in. */
    SignedOut,

    SignedIn,
    ;

    /** Every screen that may be showing in this state; the first one is where the state starts. */
    val screens: List<KClass<*>>
        get() =
            when (this) {
                NotConfigured -> {
                    listOf(SetupRoute::class)
                }

                SignedOut -> {
                    listOf(LoginRoute::class)
                }

                SignedIn -> {
                    listOf(
                        NotesRoute::class,
                        EditorRoute::class,
                        ArchiveRoute::class,
                        TrashRoute::class,
                        SettingsRoute::class,
                        AboutRoute::class,
                    )
                }
            }

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
