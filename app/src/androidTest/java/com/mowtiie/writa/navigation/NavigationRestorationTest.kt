package com.mowtiie.writa.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * What survives the system recreating the activity or killing the process: the back stack, and the note id that
 * the editor's ViewModel reads from its saved state.
 */
class NavigationRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    /** Stands in for a ViewModel that, like the editor's, reads its arguments from the saved state. */
    class Probe(
        val handle: SavedStateHandle,
    ) : ViewModel()

    private fun noteIdSeenByTheEditor(route: EditorRoute): Long? {
        var seen: Long? = null
        rule.setContent {
            NavHost(rememberNavController(), startDestination = route) {
                composable<EditorRoute> { seen = viewModel<Probe>().handle.get<Long>(EditorRoute.NOTE_ID_KEY) }
            }
        }
        rule.waitForIdle()
        return seen
    }

    @Test
    fun theEditorFindsTheNoteIdItWasOpenedWith() {
        assertEquals(5L, noteIdSeenByTheEditor(EditorRoute(5)))
    }

    @Test
    fun aLongNoteIdIsKeptWhole() {
        val big = 9_007_199_254_740_993L

        assertEquals(big, noteIdSeenByTheEditor(EditorRoute(big)))
    }

    @Test
    fun aNewNoteIsOpenedWithNoNoteId() {
        val seen = noteIdSeenByTheEditor(EditorRoute())

        assertEquals(EditorRoute.NEW_NOTE, seen ?: EditorRoute.NEW_NOTE)
    }

    @Test
    fun theOpenEditorIsStillThereAfterTheActivityIsRecreatedAndGoesBackToTheNotes() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            WritaNavHost(
                authState = AuthState.SignedIn,
                notes = { navigation ->
                    Column {
                        Text("notes", Modifier.testTag("notes"))
                        Button(onClick = navigation.onCreateNote, modifier = Modifier.testTag("new")) { Text("new") }
                    }
                },
                editor = { onClose ->
                    Column {
                        Text("editor", Modifier.testTag("editor"))
                        Button(onClick = onClose, modifier = Modifier.testTag("close")) { Text("close") }
                    }
                },
            )
        }
        rule.onNodeWithTag("new").performClick()
        rule.onNodeWithTag("editor").assertIsDisplayed()

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("editor").assertIsDisplayed()
        rule.onNodeWithTag("close").performClick()
        rule.onNodeWithTag("notes").assertIsDisplayed()
    }
}
