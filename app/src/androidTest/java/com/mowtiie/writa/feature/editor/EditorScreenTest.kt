package com.mowtiie.writa.feature.editor

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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.mowtiie.writa.R
import com.mowtiie.writa.core.ui.theme.WritaTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditorScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(id: Int) = context.getString(id)

    private fun text(
        id: Int,
        vararg args: Any,
    ) = context.getString(id, *args)

    private var state by mutableStateOf(EditorUiState())
    private val calls = mutableListOf<String>()
    private val events = Channel<EditorEvent>(Channel.BUFFERED)

    private val actions =
        EditorActions(
            onTitleChange = { state = state.copy(draft = state.draft.copy(title = it)) },
            onContentChange = { state = state.copy(draft = state.draft.copy(content = it)) },
            onPinToggle = { calls += "pin" },
            onTagInputChange = { state = state.copy(tagInput = it) },
            onTagCommit = { calls += "commit" },
            onTagPicked = { calls += "picked:$it" },
            onTagRemove = { calls += "remove:$it" },
            onSave = { calls += "save" },
            onArchiveToggle = { calls += "archive" },
            onTrash = { calls += "trash" },
            onMessageShown = {
                calls += "shown"
                state = state.copy(message = null)
            },
            onClose = { calls += "close" },
        )

    private fun show(initial: EditorUiState = EditorUiState()) {
        state = initial
        rule.setContent { WritaTheme { EditorScreen(state, actions, events.receiveAsFlow()) } }
    }

    private val existing =
        EditorUiState(isNew = false, draft = NoteDraft("Seven", "body"), original = NoteDraft("Seven", "body"))

    private val isTextField = SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)

    private fun titleField() = rule.onAllNodes(isTextField)[0]

    private fun tagField() = rule.onAllNodes(isTextField)[1]

    private fun contentField() = rule.onAllNodes(isTextField)[2]

    // ---------------------------- what is shown ----------------------------

    @Test
    fun aNewNoteHasNoDeleteOrArchive() {
        show()

        rule.onNodeWithText(text(R.string.editor_title_new)).assertIsDisplayed()
        rule.onNodeWithContentDescription(text(R.string.editor_menu_save)).assertIsDisplayed()
        rule.onNodeWithContentDescription(text(R.string.editor_menu_pin)).assertIsDisplayed()
        rule.onAllNodesWithContentDescription(text(R.string.editor_menu_delete)).assertCountEquals(0)
        rule.onAllNodesWithContentDescription(text(R.string.notes_menu_more)).assertCountEquals(0)
    }

    @Test
    fun anExistingNoteCanBeDeletedAndArchivedFromTheMenu() {
        show(existing)

        rule.onNodeWithText(text(R.string.editor_title_edit)).assertIsDisplayed()
        rule.onNodeWithContentDescription(text(R.string.editor_menu_delete)).assertIsDisplayed()
        rule.onNodeWithContentDescription(text(R.string.notes_menu_more)).performClick()
        rule.onNodeWithText(text(R.string.editor_menu_archive)).performClick()

        assertEquals(listOf("archive"), calls)
    }

    @Test
    fun anArchivedNoteOffersUnarchive() {
        show(existing.copy(archived = true))

        rule.onNodeWithContentDescription(text(R.string.notes_menu_more)).performClick()

        rule.onNodeWithText(text(R.string.editor_menu_unarchive)).assertIsDisplayed()
    }

    @Test
    fun theFieldsShowTheDraft() {
        show(existing)

        titleField().assert(hasText("Seven", substring = true))
        contentField().assert(hasText("body", substring = true))
    }

    @Test
    fun theTitleIsMarkedAsAHeading() {
        show()

        rule
            .onNodeWithText(text(R.string.editor_title_new))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    // ---------------------------- actions ----------------------------

    @Test
    fun typingReachesTheTitleAndTheContent() {
        show()

        titleField().performTextInput("Groceries")
        contentField().performTextInput("milk")

        assertEquals("Groceries", state.draft.title)
        assertEquals("milk", state.draft.content)
    }

    @Test
    fun saveAndPinDoWhatTheySay() {
        show()

        rule.onNodeWithContentDescription(text(R.string.editor_menu_save)).performClick()
        rule.onNodeWithContentDescription(text(R.string.editor_menu_pin)).performClick()

        assertEquals(listOf("save", "pin"), calls)
    }

    @Test
    fun aPinnedNoteOffersToUnpinIt() {
        show(EditorUiState(draft = NoteDraft(pinned = true)))

        rule.onNodeWithContentDescription(text(R.string.editor_menu_unpin)).assertIsDisplayed()
    }

    @Test
    fun whileSavingTheSaveAndDeleteButtonsAreDisabled() {
        show(existing.copy(isSaving = true))

        rule.onNodeWithContentDescription(text(R.string.editor_menu_save)).assertIsNotEnabled()
        rule.onNodeWithContentDescription(text(R.string.editor_menu_delete)).assertIsNotEnabled()
    }

    @Test
    fun aMissingTitleShowsOnTheFieldAsTextAndAsASemanticError() {
        show(EditorUiState(titleMissing = true))

        rule.onNodeWithText(text(R.string.editor_error_empty_title)).assertIsDisplayed()
        titleField().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
    }

    // ---------------------------- tags ----------------------------

    @Test
    fun eachTagIsAChipThatRemovesItself() {
        show(EditorUiState(draft = NoteDraft(tags = listOf("home", "work"))))

        rule.onNodeWithText("home").assertIsDisplayed()
        rule.onNodeWithContentDescription(text(R.string.editor_remove_tag, "work")).performClick()

        assertEquals(listOf("remove:work"), calls)
    }

    @Test
    fun theKeyboardDoneKeyAddsTheTypedTag() {
        show()

        tagField().performTextInput("home")
        tagField().performImeAction()

        assertEquals("home", state.tagInput)
        assertEquals(listOf("commit"), calls)
    }

    @Test
    fun theTagLimitIsExplainedOnTheTagField() {
        show(EditorUiState(tagLimitReached = true))

        rule.onNodeWithText(text(R.plurals.editor_error_tags_limit, 10)).assertIsDisplayed()
    }

    @Test
    fun matchingTagsOfOtherNotesAreSuggestedAndPicked() {
        show(EditorUiState(knownTags = listOf("Home", "Work")))

        tagField().performTextInput("ho")
        rule.onNodeWithText("Home").performClick()

        assertEquals(listOf("picked:Home"), calls)
    }

    private fun text(
        plural: Int,
        count: Int,
    ): String = context.resources.getQuantityString(plural, count, count)

    // ---------------------------- leaving ----------------------------

    @Test
    fun leavingWithoutChangesClosesAtOnce() {
        show(existing)

        rule.onNodeWithContentDescription(text(R.string.navigate_up)).performClick()

        assertEquals(listOf("close"), calls)
    }

    @Test
    fun leavingWithChangesAsksFirstAndKeepEditingStays() {
        show(existing.copy(draft = NoteDraft("Seven!", "body")))

        rule.onNodeWithContentDescription(text(R.string.navigate_up)).performClick()
        rule.onNodeWithText(text(R.string.editor_discard_title)).assertIsDisplayed()
        assertTrue(calls.isEmpty())

        rule.onNodeWithText(text(R.string.editor_keep_editing)).performClick()

        assertTrue(calls.isEmpty())
        rule.onAllNodesWithText(text(R.string.editor_discard_title)).assertCountEquals(0)
    }

    @Test
    fun discardingTheChangesCloses() {
        show(existing.copy(draft = NoteDraft("Seven!", "body")))

        rule.onNodeWithContentDescription(text(R.string.navigate_up)).performClick()
        rule.onNodeWithText(text(R.string.editor_discard)).performClick()

        assertEquals(listOf("close"), calls)
    }

    @Test
    fun movingToTheTrashAsksFirst() {
        show(existing)

        rule.onNodeWithContentDescription(text(R.string.editor_menu_delete)).performClick()
        rule.onNodeWithText(text(R.string.notes_trash_confirm_title)).assertIsDisplayed()
        assertTrue(calls.isEmpty())

        rule.onNodeWithText(text(R.string.editor_menu_delete)).performClick()

        assertEquals(listOf("trash"), calls)
    }

    @Test
    fun cancellingTheTrashConfirmationDoesNothing() {
        show(existing)

        rule.onNodeWithContentDescription(text(R.string.editor_menu_delete)).performClick()
        rule.onNodeWithText(text(R.string.notes_confirm_cancel)).performClick()

        assertTrue(calls.isEmpty())
    }

    @Test
    fun theCloseEventClosesTheScreen() {
        show()

        events.trySend(EditorEvent.Close)
        rule.waitForIdle()

        assertEquals(listOf("close"), calls)
    }

    // ---------------------------- states ----------------------------

    @Test
    fun whileTheNoteLoadsNoFormIsShown() {
        show(EditorUiState(load = EditorLoad.Loading, isNew = false))

        rule.onNodeWithText(text(R.string.editor_loading)).assertIsDisplayed()
        rule.onAllNodes(isTextField).assertCountEquals(0)
        rule.onAllNodesWithContentDescription(text(R.string.editor_menu_save)).assertCountEquals(0)
    }

    @Test
    fun aNoteThatCannotBeOpenedSaysSoAndCanBeLeft() {
        show(EditorUiState(load = EditorLoad.Unavailable, isNew = false))

        rule.onNodeWithText(text(R.string.editor_unavailable)).assertIsDisplayed()
        rule.onNodeWithContentDescription(text(R.string.navigate_up)).performClick()

        assertEquals(listOf("close"), calls)
    }

    @Test
    fun aFailureIsAnnouncedOnceAndThenCleared() {
        show(EditorUiState(message = EditorMessage.SaveFailed))

        rule.onNodeWithText(text(R.string.editor_save_failed)).assertIsDisplayed()
        rule.waitUntil(timeoutMillis = SNACKBAR_WAIT_MS) { calls == listOf("shown") }

        assertEquals(listOf("shown"), calls)
    }

    @Test
    fun theContentFieldIsReachableByScrolling() {
        show()

        contentField().performScrollTo().assertIsDisplayed()
    }

    private companion object {
        const val SNACKBAR_WAIT_MS = 10_000L
    }
}
