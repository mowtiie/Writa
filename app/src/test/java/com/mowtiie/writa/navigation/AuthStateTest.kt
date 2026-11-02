package com.mowtiie.writa.navigation

import com.mowtiie.writa.core.datastore.Connection
import com.mowtiie.writa.core.security.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun eachStateStartsOnItsFirstScreen() {
        for (state in AuthState.entries) {
            assertEquals(state.route::class, state.screens.first())
        }
    }

    @Test
    fun onlyASignedInUserMayBeOnTheNoteScreens() {
        val noteScreens =
            listOf(EditorRoute::class, ArchiveRoute::class, TrashRoute::class, SettingsRoute::class, AboutRoute::class)

        assertTrue(AuthState.SignedIn.screens.containsAll(noteScreens + NotesRoute::class))
        for (state in listOf(AuthState.NotConfigured, AuthState.SignedOut)) {
            assertEquals(1, state.screens.size)
            assertTrue(noteScreens.none { it in state.screens })
        }
    }

    @Test
    fun signingOutOrChangingTheServerMovesTheStateBackwards() {
        assertEquals(AuthState.SignedOut, AuthState.of(connection, null))
        assertEquals(AuthState.NotConfigured, AuthState.of(null, null))
    }
}
