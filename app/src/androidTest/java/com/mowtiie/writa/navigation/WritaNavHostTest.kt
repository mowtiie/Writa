package com.mowtiie.writa.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
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
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalLayoutApi::class)
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
                notes = { navigation ->
                    FlowRow {
                        Text("notes", Modifier.testTag(NOTES))
                        Button(
                            onClick = navigation.onOpenArchive,
                            modifier = Modifier.testTag(OPEN_ARCHIVE),
                        ) { Text("archive") }
                        Button(
                            onClick = navigation.onOpenTrash,
                            modifier = Modifier.testTag(OPEN_TRASH),
                        ) { Text("trash") }
                        Button(
                            onClick = navigation.onOpenSettings,
                            modifier = Modifier.testTag(OPEN_SETTINGS),
                        ) { Text("settings") }
                        Button(onClick = navigation.onCreateNote, modifier = Modifier.testTag(NEW_NOTE)) { Text("new") }
                        Button(onClick = { navigation.onOpenNote(OPENED_ID) }, modifier = Modifier.testTag(OPEN_NOTE)) {
                            Text("open")
                        }
                    }
                },
                archive = { navigation ->
                    Column {
                        Text("archive", Modifier.testTag(ARCHIVE))
                        Button(onClick = { navigation.onOpenNote(OPENED_ID) }, modifier = Modifier.testTag(OPEN_NOTE)) {
                            Text("open")
                        }
                        Button(onClick = navigation.onBack, modifier = Modifier.testTag(BACK)) { Text("back") }
                    }
                },
                settings = { onBack ->
                    Column {
                        Text("settings", Modifier.testTag(SETTINGS))
                        Button(onClick = onBack, modifier = Modifier.testTag(BACK)) { Text("back") }
                    }
                },
                trash = { onBack ->
                    Column {
                        Text("trash", Modifier.testTag(TRASH))
                        Button(onClick = onBack, modifier = Modifier.testTag(BACK)) { Text("back") }
                    }
                },
                editor = { onClose ->
                    Column {
                        Text("editor", Modifier.testTag(EDITOR))
                        Button(onClick = onClose, modifier = Modifier.testTag(CLOSE_EDITOR)) { Text("close") }
                    }
                },
            )
        }
    }

    private fun change(state: AuthState) {
        rule.runOnUiThread { authState = state }
        rule.waitForIdle()
    }

    private fun assertOnly(tag: String) {
        for (other in listOf(
            SETUP,
            LOGIN,
            NOTES,
            EDITOR,
            ARCHIVE,
            TRASH,
        ) - tag) {
            rule.onAllNodesWithTag(other).assertCountEquals(0)
        }
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

        for (tag in listOf(
            SETUP,
            LOGIN,
            NOTES,
            EDITOR,
            ARCHIVE,
            TRASH,
        )) {
            rule.onAllNodesWithTag(tag).assertCountEquals(0)
        }
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

    @Test
    fun creatingANoteOpensTheEditorAndClosingItReturnsToTheNotes() {
        show(AuthState.SignedIn)

        rule.onNodeWithTag(NEW_NOTE).performClick()
        assertOnly(EDITOR)

        rule.onNodeWithTag(CLOSE_EDITOR).performClick()
        assertOnly(NOTES)
    }

    @Test
    fun openingANoteShowsTheEditor() {
        show(AuthState.SignedIn)

        rule.onNodeWithTag(OPEN_NOTE).performClick()

        assertOnly(EDITOR)
    }

    @Test
    fun aSessionThatEndsWhileTheEditorIsOpenMovesToLogin() {
        show(AuthState.SignedIn)
        rule.onNodeWithTag(NEW_NOTE).performClick()

        change(AuthState.SignedOut)

        assertOnly(LOGIN)
    }

    @Test
    fun theArchiveOpensFromTheNotesAndBackReturns() {
        show(AuthState.SignedIn)

        rule.onNodeWithTag(OPEN_ARCHIVE).performClick()
        assertOnly(ARCHIVE)

        rule.onNodeWithTag(BACK).performClick()
        assertOnly(NOTES)
    }

    @Test
    fun aNoteOpenedFromTheArchiveShowsTheEditorAndClosingReturnsToTheArchive() {
        show(AuthState.SignedIn)
        rule.onNodeWithTag(OPEN_ARCHIVE).performClick()

        rule.onNodeWithTag(OPEN_NOTE).performClick()
        assertOnly(EDITOR)

        rule.onNodeWithTag(CLOSE_EDITOR).performClick()
        assertOnly(ARCHIVE)
    }

    @Test
    fun theTrashOpensFromTheNotesAndBackReturns() {
        show(AuthState.SignedIn)

        rule.onNodeWithTag(OPEN_TRASH).performClick()
        assertOnly(TRASH)

        rule.onNodeWithTag(BACK).performClick()
        assertOnly(NOTES)
    }

    @Test
    fun aSessionThatEndsInTheTrashMovesToLogin() {
        show(AuthState.SignedIn)
        rule.onNodeWithTag(OPEN_TRASH).performClick()

        change(AuthState.SignedOut)

        assertOnly(LOGIN)
    }

    @Test
    fun theSettingsOpenFromTheNotesAndBackReturns() {
        show(AuthState.SignedIn)

        rule.onNodeWithTag(OPEN_SETTINGS).performClick()
        assertOnly(SETTINGS)

        rule.onNodeWithTag(BACK).performClick()
        assertOnly(NOTES)
    }

    @Test
    fun forgettingTheServerFromTheSettingsMovesStraightToSetup() {
        show(AuthState.SignedIn)
        rule.onNodeWithTag(OPEN_SETTINGS).performClick()

        change(AuthState.NotConfigured)

        assertOnly(SETUP)
    }

    private companion object {
        const val SETUP = "setup"
        const val LOGIN = "login"
        const val NOTES = "notes"
        const val EDITOR = "editor"
        const val ARCHIVE = "archive"
        const val TRASH = "trash"
        const val SETTINGS = "settings"
        const val OPEN_SETTINGS = "open-settings"
        const val OPEN_ARCHIVE = "open-archive"
        const val OPEN_TRASH = "open-trash"
        const val BACK = "back"
        const val NEW_NOTE = "new"
        const val OPEN_NOTE = "open"
        const val CLOSE_EDITOR = "close"
        const val OPENED_ID = 5L
    }
}
