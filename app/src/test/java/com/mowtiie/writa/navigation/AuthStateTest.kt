package com.mowtiie.writa.navigation

import com.mowtiie.writa.core.datastore.Connection
import com.mowtiie.writa.core.security.Session
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthStateTest {
    private val connection = Connection("https://a.supabase.co", "key")
    private val session = Session("access", "refresh", "me@example.com")

    @Test
    fun withoutAServerTheUserIsNotConfigured() {
        assertEquals(AuthState.NotConfigured, AuthState.of(connection = null, session = null))
    }

    @Test
    fun aServerWithoutASessionIsSignedOut() {
        assertEquals(AuthState.SignedOut, AuthState.of(connection, session = null))
    }

    @Test
    fun aServerAndASessionIsSignedIn() {
        assertEquals(AuthState.SignedIn, AuthState.of(connection, session))
    }

    @Test
    fun aSessionWithoutAServerStillNeedsASetup() {
        assertEquals(AuthState.NotConfigured, AuthState.of(connection = null, session = session))
    }

    @Test
    fun eachStateBelongsOnItsOwnScreen() {
        assertEquals(SetupRoute, AuthState.NotConfigured.route)
        assertEquals(LoginRoute, AuthState.SignedOut.route)
        assertEquals(NotesRoute, AuthState.SignedIn.route)
    }

    @Test
    fun signingOutOrChangingTheServerMovesTheStateBackwards() {
        assertEquals(AuthState.SignedOut, AuthState.of(connection, null))
        assertEquals(AuthState.NotConfigured, AuthState.of(null, null))
    }
}
