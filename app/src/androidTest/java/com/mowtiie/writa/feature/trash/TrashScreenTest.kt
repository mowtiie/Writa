package com.mowtiie.writa.feature.trash

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.mowtiie.writa.R
import com.mowtiie.writa.core.ui.theme.WritaTheme
import com.mowtiie.writa.feature.notes.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TrashScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(id: Int) = context.getString(id)

    private var state by mutableStateOf(TrashUiState(isLoaded = true))
    private val calls = mutableListOf<String>()

    private val actions =
        TrashActions(
            onRestore = { calls += "restore:$it" },
            onDeleteForever = { calls += "delete:$it" },
            onEmptyTrash = { calls += "empty" },
            onMessageShown = {
                calls += "shown"
                state = state.copy(actionFailed = false)
            },
        )

    private fun note(id: Long) =
        Note(
            id,
            "Note $id",
            "body $id",
            emptyList(),
            false,
            false,
            "2026-11-01T10:00:00+00:00",
            null,
            "2026-11-02T00:00:00Z",
        )

    private fun show(initial: TrashUiState) {
        state = initial
        rule.setContent { WritaTheme { TrashScreen(state, actions, onBack = { calls += "back" }) } }
    }

    private fun withNotes() = TrashUiState(listOf(note(1), note(2)), isLoaded = true)

    private fun openMenu() = rule.onNodeWithContentDescription(text(R.string.notes_menu_more)).performClick()

    @Test
    fun theTrashedNotesAreListedUnderTheirTitle() {
        show(withNotes())

        rule.onNodeWithText(text(R.string.trash_title)).assertIsDisplayed()
        rule.onNodeWithText("Note 1").assertIsDisplayed()
        rule.onAllNodesWithText(text(R.string.trash_empty)).assertCountEquals(0)
    }

    @Test
    fun theTitleIsMarkedAsAHeading() {
        show(withNotes())

        rule
            .onNodeWithText(text(R.string.trash_title))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun anEmptyTrashSaysSo() {
        show(TrashUiState(isLoaded = true))

        rule.onNodeWithText(text(R.string.trash_empty)).assertIsDisplayed()
    }

    @Test
    fun beforeTheNotesAreReadNothingIsSaidAboutBeingEmpty() {
        show(TrashUiState(isLoaded = false))

        rule.onAllNodesWithText(text(R.string.trash_empty)).assertCountEquals(0)
    }

    @Test
    fun tappingANoteOffersToRestoreIt() {
        show(withNotes())

        rule.onNodeWithText("Note 2").performClick()
        rule.onNodeWithText(text(R.string.trash_restore)).performClick()

        assertEquals(listOf("restore:2"), calls)
    }

    @Test
    fun aLongPressOffersTheSameActions() {
        show(withNotes())

        rule.onNodeWithText("Note 1").performTouchInput { longClick() }

        rule.onNodeWithText(text(R.string.trash_restore)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.trash_delete_forever)).assertIsDisplayed()
    }

    @Test
    fun deletingForeverAsksFirst() {
        show(withNotes())

        rule.onNodeWithText("Note 1").performClick()
        rule.onNodeWithText(text(R.string.trash_delete_forever)).performClick()
        rule.onNodeWithText(text(R.string.trash_delete_forever_title)).assertIsDisplayed()
        assertTrue(calls.isEmpty())

        rule.onAllNodesWithText(text(R.string.trash_delete_forever))[0].performClick()

        assertEquals(listOf("delete:1"), calls)
    }

    @Test
    fun emptyingTheTrashAsksFirst() {
        show(withNotes())

        openMenu()
        rule.onNodeWithText(text(R.string.trash_menu_empty)).performClick()
        rule.onNodeWithText(text(R.string.trash_empty_title)).assertIsDisplayed()
        assertTrue(calls.isEmpty())

        rule.onAllNodesWithText(text(R.string.trash_menu_empty))[0].performClick()

        assertEquals(listOf("empty"), calls)
    }

    @Test
    fun cancellingTheEmptyingDoesNothing() {
        show(withNotes())

        openMenu()
        rule.onNodeWithText(text(R.string.trash_menu_empty)).performClick()
        rule.onNodeWithText(text(R.string.notes_confirm_cancel)).performClick()

        assertTrue(calls.isEmpty())
    }

    @Test
    fun anEmptyTrashCannotBeEmptied() {
        show(TrashUiState(isLoaded = true))

        openMenu()

        rule.onNodeWithText(text(R.string.trash_menu_empty)).assertIsNotEnabled()
    }

    @Test
    fun backLeavesTheScreen() {
        show(withNotes())

        rule.onNodeWithContentDescription(text(R.string.navigate_up)).performClick()

        assertEquals(listOf("back"), calls)
    }

    @Test
    fun aFailedChangeIsAnnouncedOnceAndThenCleared() {
        show(withNotes().copy(actionFailed = true))

        rule.onNodeWithText(text(R.string.notes_action_failed)).assertIsDisplayed()
        rule.waitUntil(timeoutMillis = SNACKBAR_WAIT_MS) { calls == listOf("shown") }
    }

    private companion object {
        const val SNACKBAR_WAIT_MS = 10_000L
    }
}
