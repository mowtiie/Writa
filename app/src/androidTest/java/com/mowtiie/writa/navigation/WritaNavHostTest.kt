package com.mowtiie.writa.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class WritaNavHostTest {
    @get:Rule
    val rule = createComposeRule()

    private var authState by mutableStateOf<AuthState?>(null)

    private fun show(initial: AuthState?) {
        authState = initial
        rule.setContent {
            WritaNavHost(
                authState = authState,
                setup = { Text("setup", Modifier.testTag(SETUP)) },
                login = { Text("login", Modifier.testTag(LOGIN)) },
                notes = { Text("notes", Modifier.testTag(NOTES)) },
            )
        }
    }

    private fun change(state: AuthState) {
        rule.runOnUiThread { authState = state }
        rule.waitForIdle()
    }

    private fun assertOnly(tag: String) {
        for (other in listOf(SETUP, LOGIN, NOTES) - tag) rule.onAllNodesWithTag(other).assertCountEquals(0)
        rule.onNodeWithTag(tag).assertIsDisplayed()
    }

    @Test
    fun withoutAServerTheAppStartsOnSetup() {
        show(AuthState.NotConfigured)

        assertOnly(SETUP)
    }

    @Test
    fun withAServerButNoSessionTheAppStartsOnLogin() {
        show(AuthState.SignedOut)

        assertOnly(LOGIN)
    }

    @Test
    fun withASessionTheAppStartsOnTheNotes() {
        show(AuthState.SignedIn)

        assertOnly(NOTES)
    }

    @Test
    fun nothingIsDrawnUntilTheStateIsKnown() {
        show(null)

        for (tag in listOf(SETUP, LOGIN, NOTES)) rule.onAllNodesWithTag(tag).assertCountEquals(0)
    }

    @Test
    fun theScreenAppearsOnceTheStateIsKnown() {
        show(null)

        change(AuthState.SignedOut)

        assertOnly(LOGIN)
    }

    @Test
    fun savingTheServerMovesFromSetupToLogin() {
        show(AuthState.NotConfigured)

        change(AuthState.SignedOut)

        assertOnly(LOGIN)
    }

    @Test
    fun signingInMovesFromLoginToTheNotes() {
        show(AuthState.SignedOut)

        change(AuthState.SignedIn)

        assertOnly(NOTES)
    }

    @Test
    fun aSessionThatEndsWhileTheNotesAreOpenMovesToLogin() {
        show(AuthState.SignedIn)

        change(AuthState.SignedOut)

        assertOnly(LOGIN)
    }

    @Test
    fun changingTheServerMovesStraightBackToSetup() {
        show(AuthState.SignedIn)

        change(AuthState.NotConfigured)

        assertOnly(SETUP)
    }

    @Test
    fun aWholeJourneyEndsWhereTheStateSays() {
        show(AuthState.NotConfigured)

        for (state in listOf(
            AuthState.SignedOut,
            AuthState.SignedIn,
            AuthState.SignedOut,
            AuthState.NotConfigured,
            AuthState.SignedOut,
        )) {
            change(state)
            assertOnly(
                when (state) {
                    AuthState.NotConfigured -> SETUP
                    AuthState.SignedOut -> LOGIN
                    AuthState.SignedIn -> NOTES
                },
            )
        }
    }

    @Test
    fun theSameStateTwiceChangesNothing() {
        show(AuthState.SignedOut)

        change(AuthState.SignedOut)

        assertOnly(LOGIN)
    }

    private companion object {
        const val SETUP = "setup"
        const val LOGIN = "login"
        const val NOTES = "notes"
    }
}
