package com.mowtiie.writa.feature.archive

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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

class ArchiveScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(id: Int) = context.getString(id)

    private var state by mutableStateOf(ArchiveUiState(isLoaded = true))
    private val calls = mutableListOf<String>()

    private val actions =
        ArchiveActions(
            onUnarchive = { calls += "unarchive:$it" },
            onMoveToTrash = { calls += "trash:$it" },
            onMessageShown = {
                calls += "shown"
                state = state.copy(actionFailed = false)
            },
        )

    private fun note(id: Long) =
        Note(id, "Note $id", "body $id", listOf("tag"), false, true, "2026-11-01T10:00:00+00:00", null, null)

    private fun show(initial: ArchiveUiState) {
        state = initial
        rule.setContent {
            WritaTheme {
                ArchiveScreen(
                    state,
                    actions,
                    onOpenNote = { calls += "open:$it" },
                    onBack = { calls += "back" },
                )
            }
        }
    }

    private fun withNotes() = ArchiveUiState(listOf(note(1), note(2)), isLoaded = true)

    @Test
    fun theArchivedNotesAreListedUnderTheirTitle() {
        show(withNotes())

        rule.onNodeWithText(text(R.string.archive_title)).assertIsDisplayed()
        rule.onNodeWithText("Note 1").assertIsDisplayed()
        rule.onNodeWithText("Note 2").assertIsDisplayed()
        rule.onAllNodesWithText(text(R.string.archive_empty)).assertCountEquals(0)
    }

    @Test
    fun theTitleIsMarkedAsAHeading() {
        show(withNotes())

        rule
            .onNodeWithText(text(R.string.archive_title))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun withoutArchivedNotesTheScreenSaysSo() {
        show(ArchiveUiState(isLoaded = true))

        rule.onNodeWithText(text(R.string.archive_empty)).assertIsDisplayed()
    }

    @Test
    fun beforeTheNotesAreReadNothingIsSaidAboutBeingEmpty() {
        show(ArchiveUiState(isLoaded = false))

        rule.onAllNodesWithText(text(R.string.archive_empty)).assertCountEquals(0)
    }

    @Test
    fun tappingANoteOpensIt() {
        show(withNotes())

        rule.onNodeWithText("Note 2").performClick()

        assertEquals(listOf("open:2"), calls)
    }

    @Test
    fun backLeavesTheScreen() {
        show(withNotes())

        rule.onNodeWithContentDescription(text(R.string.navigate_up)).performClick()

        assertEquals(listOf("back"), calls)
    }

    @Test
    fun aLongPressOffersToUnarchive() {
        show(withNotes())

        rule.onNodeWithText("Note 1").performTouchInput { longClick() }
        rule.onNodeWithText(text(R.string.archive_unarchive)).performClick()

        assertEquals(listOf("unarchive:1"), calls)
        rule.onAllNodesWithText(text(R.string.archive_unarchive)).assertCountEquals(0)
    }

    @Test
    fun movingToTheTrashAsksFirst() {
        show(withNotes())

        rule.onNodeWithText("Note 1").performTouchInput { longClick() }
        rule.onNodeWithText(text(R.string.notes_action_trash)).performClick()
        rule.onNodeWithText(text(R.string.notes_trash_confirm_title)).assertIsDisplayed()
        assertTrue(calls.isEmpty())

        rule.onAllNodesWithText(text(R.string.notes_action_trash))[0].performClick()

        assertEquals(listOf("trash:1"), calls)
    }

    @Test
    fun cancellingTheConfirmationDoesNothing() {
        show(withNotes())

        rule.onNodeWithText("Note 1").performTouchInput { longClick() }
        rule.onNodeWithText(text(R.string.notes_action_trash)).performClick()
        rule.onNodeWithText(text(R.string.notes_confirm_cancel)).performClick()

        assertTrue(calls.isEmpty())
    }

    @Test
    fun screenReadersGetTheSameActionsOnTheNote() {
        show(withNotes())

        val labels =
            rule
                .onNodeWithText("Note 1")
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .map { it.label }

        assertEquals(listOf(text(R.string.archive_unarchive), text(R.string.notes_action_trash)), labels)
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
