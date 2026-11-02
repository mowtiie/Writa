package com.mowtiie.writa.core.ui

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.mowtiie.writa.R
import com.mowtiie.writa.core.ui.theme.WritaTheme
import com.mowtiie.writa.feature.auth.LoginScreen
import com.mowtiie.writa.feature.auth.LoginUiState
import com.mowtiie.writa.feature.editor.EditorActions
import com.mowtiie.writa.feature.editor.EditorScreen
import com.mowtiie.writa.feature.editor.EditorUiState
import com.mowtiie.writa.feature.editor.NoteDraft
import com.mowtiie.writa.feature.notes.Note
import com.mowtiie.writa.feature.notes.list.NoteListUiState
import com.mowtiie.writa.feature.notes.list.NotesActions
import com.mowtiie.writa.feature.notes.list.NotesNavigation
import com.mowtiie.writa.feature.notes.list.NotesScreen
import com.mowtiie.writa.feature.settings.SettingsActions
import com.mowtiie.writa.feature.settings.SettingsScreen
import com.mowtiie.writa.feature.settings.SettingsUiState
import com.mowtiie.writa.feature.trash.TrashActions
import com.mowtiie.writa.feature.trash.TrashScreen
import com.mowtiie.writa.feature.trash.TrashUiState
import org.junit.Rule
import org.junit.Test

/** A dialog or toggle the user opened is still open after the system recreates the activity. */
class StateRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(id: Int) = context.getString(id)

    private val tester = StateRestorationTester(rule)

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        tester.setContent { WritaTheme { content() } }
    }

    @Test
    fun theEditorsDiscardQuestionIsStillAsked() {
        val changed = EditorUiState(isNew = false, draft = NoteDraft("Changed"), original = NoteDraft("Original"))
        show { EditorScreen(changed, EditorActions()) }
        rule.onNodeWithContentDescription(text(R.string.navigate_up)).performClick()

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithText(text(R.string.editor_discard_title)).assertIsDisplayed()
    }

    @Test
    fun theEditorsTrashQuestionIsStillAsked() {
        show { EditorScreen(EditorUiState(isNew = false, draft = NoteDraft("Note")), EditorActions()) }
        rule.onNodeWithContentDescription(text(R.string.editor_menu_delete)).performClick()

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithText(text(R.string.notes_trash_confirm_title)).assertIsDisplayed()
    }

    @Test
    fun theSettingsThemeChoiceIsStillOpen() {
        show { SettingsScreen(SettingsUiState(), SettingsActions(), dynamicColorAvailable = true, onBack = {}) }
        rule.onNodeWithText(text(R.string.settings_theme)).performClick()

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithText(text(R.string.settings_theme_battery)).assertIsDisplayed()
    }

    @Test
    fun theChangeServerQuestionIsStillAsked() {
        show { SettingsScreen(SettingsUiState(), SettingsActions(), dynamicColorAvailable = true, onBack = {}) }
        rule.onNodeWithText(text(R.string.settings_change_server)).performScrollTo().performClick()

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithText(text(R.string.settings_change_server_title)).assertIsDisplayed()
    }

    @Test
    fun theEmptyTrashQuestionIsStillAsked() {
        val note =
            Note(1, "Old", null, emptyList(), false, false, "2026-11-01T10:00:00+00:00", null, "2026-11-02T00:00:00Z")
        show { TrashScreen(TrashUiState(listOf(note), isLoaded = true), TrashActions(), onBack = {}) }
        rule.onNodeWithContentDescription(text(R.string.notes_menu_more)).performClick()
        rule.onNodeWithText(text(R.string.trash_menu_empty)).performClick()

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithText(text(R.string.trash_empty_title)).assertIsDisplayed()
    }

    @Test
    fun theSortChoiceOnTheNotesIsStillOpen() {
        show { NotesScreen(NoteListUiState(isLoaded = true), NotesActions(), NotesNavigation()) }
        rule.onNodeWithContentDescription(text(R.string.notes_menu_sort)).performClick()

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithText(text(R.string.notes_sort_title)).assertIsDisplayed()
    }

    @Test
    fun theSignOutQuestionIsStillAsked() {
        show { NotesScreen(NoteListUiState(isLoaded = true), NotesActions(), NotesNavigation()) }
        rule.onNodeWithContentDescription(text(R.string.notes_menu_more)).performClick()
        rule.onNodeWithText(text(R.string.notes_menu_sign_out)).performClick()

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithText(text(R.string.notes_sign_out_title)).assertIsDisplayed()
    }

    @Test
    fun aPasswordThatWasMadeVisibleStaysVisibleAndOtherwiseStaysHidden() {
        show { LoginScreen(LoginUiState(password = "secret"), {}, {}, {}, {}, {}, {}) }
        rule.onNodeWithContentDescription(text(R.string.login_show_password)).performClick()

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithContentDescription(text(R.string.login_hide_password)).assertIsDisplayed()
    }

    @Test
    fun aClosedDialogStaysClosed() {
        show { SettingsScreen(SettingsUiState(), SettingsActions(), dynamicColorAvailable = true, onBack = {}) }

        tester.emulateSavedInstanceStateRestore()

        rule.onAllNodesWithText(text(R.string.settings_theme_battery)).assertCountEquals(0)
        rule.onAllNodesWithContentDescription(text(R.string.login_hide_password)).assertCountEquals(0)
    }
}
