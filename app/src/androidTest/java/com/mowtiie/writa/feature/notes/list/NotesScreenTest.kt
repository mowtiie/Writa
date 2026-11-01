package com.mowtiie.writa.feature.notes.list

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.mowtiie.writa.R
import com.mowtiie.writa.core.ui.theme.WritaTheme
import com.mowtiie.writa.feature.notes.Note
import com.mowtiie.writa.feature.notes.RefreshFailure
import com.mowtiie.writa.feature.notes.Sort
import com.mowtiie.writa.feature.notes.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NotesScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(id: Int) = context.getString(id)

    private fun text(
        id: Int,
        vararg args: Any,
    ) = context.getString(id, *args)

    private var state by mutableStateOf(NoteListUiState(isLoaded = true))
    private val calls = mutableListOf<String>()

    private val actions =
        NotesActions(
            onRefresh = { calls += "refresh" },
            onSearchOpen = { calls += "searchOpen" },
            onSearchChange = { calls += "search:$it" },
            onSearchClose = { calls += "searchClose" },
            onTagToggle = { calls += "tag:$it" },
            onSortSelected = { calls += "sort:$it" },
            onSwipeDelete = { calls += "swipe:$it" },
            onUndo = { calls += "undo" },
            onSetPinned = { id, pinned -> calls += "pin:$id:$pinned" },
            onArchive = { calls += "archive:$it" },
            onMoveToTrash = { calls += "trash:$it" },
            onRetryFailed = { calls += "retry:$it" },
            onDiscardFailed = { calls += "discard:$it" },
            onMessageShown = {
                calls += "messageShown"
                state = state.copy(message = null)
            },
        )

    private val navigation =
        NotesNavigation(
            onOpenNote = { calls += "open:$it" },
            onCreateNote = { calls += "create" },
            onOpenArchive = { calls += "archiveScreen" },
            onOpenTrash = { calls += "trashScreen" },
            onOpenSettings = { calls += "settings" },
            onOpenAbout = { calls += "about" },
        )

    private fun show(initial: NoteListUiState = NoteListUiState(isLoaded = true)) {
        state = initial
        rule.setContent { WritaTheme { NotesScreen(state, actions, navigation) } }
    }

    private fun note(
        id: Long,
        title: String = "Note $id",
        content: String? = "Body of $id",
        tags: List<String> = emptyList(),
        pinned: Boolean = false,
        updatedAt: String? = null,
        syncState: SyncState = SyncState.Synced,
    ) = Note(id, title, content, tags, pinned, false, "2026-11-01T10:00:00+00:00", updatedAt, null, syncState)

    private fun items(vararg notes: Note) = notes.map { NoteListItem(it.id, it) }

    private fun stateWith(
        vararg notes: Note,
        change: (NoteListUiState) -> NoteListUiState = { it },
    ) = change(NoteListUiState(items = items(*notes), isLoaded = true))

    // ---------------------------- the rows ----------------------------

    @Test
    fun aRowShowsTheTitleAPreviewTheTagsAndTheDate() {
        show(stateWith(note(1, title = "Shopping", content = "milk and eggs", tags = listOf("home", "weekly"))))

        rule.onNodeWithText("Shopping").assertIsDisplayed()
        rule.onNodeWithText("milk and eggs").assertIsDisplayed()
        rule.onNodeWithText("#home  #weekly").assertIsDisplayed()
        rule.onNodeWithText("2026", substring = true).assertIsDisplayed()
    }

    @Test
    fun anEditedNoteSaysWhenItWasEdited() {
        show(stateWith(note(1, updatedAt = "2026-11-05T10:00:00+00:00")))

        rule.onNodeWithText(text(R.string.notes_edited, "").trim(), substring = true).assertIsDisplayed()
    }

    @Test
    fun aPinnedNoteSaysSo() {
        show(stateWith(note(1, pinned = true), note(2)))

        rule.onAllNodesWithText(text(R.string.notes_pinned)).assertCountEquals(1)
    }

    @Test
    fun unsentAndFailedNotesSaySoToo() {
        show(stateWith(note(1, syncState = SyncState.Pending), note(2, syncState = SyncState.Failed), note(3)))

        rule.onNodeWithText(text(R.string.notes_sync_pending), substring = true).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.notes_sync_failed), substring = true).assertIsDisplayed()
    }

    @Test
    fun aNoteWithoutContentShowsJustItsTitle() {
        show(stateWith(note(1, title = "Only a title", content = null)))

        rule.onNodeWithText("Only a title").assertIsDisplayed()
    }

    @Test
    fun tappingARowOpensThatNote() {
        show(stateWith(note(1), note(2)))

        rule.onNodeWithText("Note 2").performClick()

        assertEquals(listOf("open:2"), calls)
    }

    @Test
    fun theAddButtonCreatesANote() {
        show()

        rule.onNodeWithContentDescription(text(R.string.notes_add_note)).performClick()

        assertEquals(listOf("create"), calls)
    }

    // ---------------------------- empty and loading ----------------------------

    @Test
    fun anEmptyListSaysWhy() {
        val expected =
            mapOf(
                EmptyReason.NoNotes to text(R.string.notes_empty),
                EmptyReason.NoMatchForSearch("milk") to text(R.string.notes_empty_search, "milk"),
                EmptyReason.NoMatchForTags to text(R.string.notes_empty_tags),
            )
        show()

        for ((reason, message) in expected) {
            rule.runOnUiThread { state = NoteListUiState(isLoaded = true, emptyReason = reason) }
            rule.onNodeWithText(message).assertIsDisplayed()
        }
    }

    @Test
    fun nothingIsSaidAboutAnEmptyListWhileTheNotesAreStillLoading() {
        show(NoteListUiState(isLoaded = false, emptyReason = null))

        rule.onAllNodesWithText(text(R.string.notes_empty)).assertCountEquals(0)
    }

    // ---------------------------- the banner ----------------------------

    @Test
    fun noBannerWhenEverythingIsFine() {
        show(stateWith(note(1)))

        rule.onAllNodes(hasText("Showing saved notes", substring = true)).assertCountEquals(0)
    }

    @Test
    fun everyFailureHasItsOwnBanner() {
        val expected =
            mapOf(
                RefreshFailure.Offline to R.string.notes_banner_offline,
                RefreshFailure.Server to R.string.notes_banner_server,
                RefreshFailure.SecureConnection to R.string.notes_banner_secure,
                RefreshFailure.Unavailable to R.string.notes_banner_unavailable,
                RefreshFailure.SchemaOutdated to R.string.notes_banner_schema,
                RefreshFailure.Other to R.string.notes_banner_other,
            )
        show()

        for ((reason, message) in expected) {
            rule.runOnUiThread { state = NoteListUiState(isLoaded = true, banner = NotesBanner.RefreshFailed(reason)) }
            rule.onNodeWithText(text(message)).assertIsDisplayed()
        }
    }

    @Test
    fun theBannerCountsTheChangesWaitingToUpload() {
        show(NoteListUiState(isLoaded = true, banner = NotesBanner.UnsentChanges(1)))
        rule
            .onNodeWithText(
                context.resources.getQuantityString(R.plurals.notes_banner_unsent, 1, 1),
            ).assertIsDisplayed()

        rule.runOnUiThread { state = NoteListUiState(isLoaded = true, banner = NotesBanner.UnsentChanges(3)) }

        rule
            .onNodeWithText(
                context.resources.getQuantityString(R.plurals.notes_banner_unsent, 3, 3),
            ).assertIsDisplayed()
    }

    @Test
    fun tappingTheBannerTriesAgain() {
        show(NoteListUiState(isLoaded = true, banner = NotesBanner.RefreshFailed(RefreshFailure.Offline)))

        rule.onNodeWithText(text(R.string.notes_banner_offline)).performClick()

        assertEquals(listOf("refresh"), calls)
    }

    // ---------------------------- tags ----------------------------

    @Test
    fun theTagBarShowsEveryTagWithItsCount() {
        show(
            NoteListUiState(
                isLoaded = true,
                tags = listOf(TagCount("work", "work", 2, false), TagCount("home", "home", 1, true)),
            ),
        )

        rule.onNodeWithText(text(R.string.notes_tag_with_count, "work", 2)).assertIsDisplayed().assert(!isSelected)
        rule.onNodeWithText(text(R.string.notes_tag_with_count, "home", 1)).assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun tappingATagTogglesIt() {
        show(NoteListUiState(isLoaded = true, tags = listOf(TagCount("Work", "work", 2, false))))

        rule.onNodeWithText(text(R.string.notes_tag_with_count, "Work", 2)).performClick()

        assertEquals(listOf("tag:work"), calls)
    }

    @Test
    fun withoutTagsThereIsNoFilterBar() {
        show(stateWith(note(1)))

        rule.onAllNodes(hasContentDescription(text(R.string.notes_filter_by_tag))).assertCountEquals(0)
    }

    private val isSelected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)

    // ---------------------------- search ----------------------------

    @Test
    fun theSearchIconOpensTheSearch() {
        show()

        rule.onNodeWithContentDescription(text(R.string.notes_menu_search)).performClick()

        assertEquals(listOf("searchOpen"), calls)
    }

    @Test
    fun anOpenSearchShowsTheTextAndTypingChangesIt() {
        show(NoteListUiState(isLoaded = true, isSearchOpen = true, search = "mil"))

        rule.onNode(hasSetTextAction()).assert(hasText("mil", substring = true))
        rule.onNode(hasSetTextAction()).performTextInput("k")

        assertEquals(listOf("search:milk"), calls.filter { it.startsWith("search:") })
    }

    @Test
    fun anOpenSearchReplacesTheTitleAndCanBeClosed() {
        show(NoteListUiState(isLoaded = true, isSearchOpen = true))

        rule.onAllNodesWithText(text(R.string.app_name)).assertCountEquals(0)
        rule.onNodeWithContentDescription(text(R.string.notes_search_close)).performClick()

        assertEquals(listOf("searchClose"), calls)
    }

    @Test
    fun anOpenSearchHasAHint() {
        show(NoteListUiState(isLoaded = true, isSearchOpen = true))

        rule.onNodeWithText(text(R.string.notes_search_hint)).assertIsDisplayed()
    }

    // ---------------------------- menus ----------------------------

    @Test
    fun theTitleIsAHeading() {
        show()

        rule.onNodeWithText(text(R.string.app_name)).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun theOverflowMenuLeadsToTheOtherScreens() {
        show()

        for (
        (label, expected) in
        listOf(
            R.string.notes_menu_archive to "archiveScreen",
            R.string.notes_menu_trash to "trashScreen",
            R.string.notes_menu_settings to "settings",
            R.string.notes_menu_about to "about",
        )
        ) {
            rule.onNodeWithContentDescription(text(R.string.notes_menu_more)).performClick()
            rule.onNodeWithText(text(label)).performClick()
            assertEquals(expected, calls.last())
        }
    }

    @Test
    fun sortOpensADialogWithTheCurrentChoiceSelectedAndChoosingApplies() {
        show(NoteListUiState(isLoaded = true, sort = Sort.OLDEST))

        rule.onNodeWithContentDescription(text(R.string.notes_menu_more)).performClick()
        rule.onNodeWithText(text(R.string.notes_menu_sort)).performClick()

        rule.onNodeWithText(text(R.string.notes_sort_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.notes_sort_oldest)).assertIsSelected()
        rule.onNodeWithText(text(R.string.notes_sort_newest)).assert(!isSelected)

        rule.onNodeWithText(text(R.string.notes_sort_title_za)).performClick()

        assertEquals(listOf("sort:TITLE_ZA"), calls)
        rule.onAllNodesWithText(text(R.string.notes_sort_title)).assertCountEquals(0)
    }

    // ---------------------------- gestures ----------------------------

    @Test
    fun pullingTheListDownRefreshes() {
        show(stateWith(note(1), note(2)))

        rule.onRoot().performTouchInput { swipeDown(startY = height * 0.25f, endY = height * 0.85f) }
        rule.waitForIdle()

        assertTrue(calls.contains("refresh"))
    }

    @Test
    fun swipingARowEitherWayMovesItToTheTrashWithUndo() {
        show(stateWith(note(1), note(2)))

        rule.onNodeWithText("Note 1").performTouchInput { swipeLeft() }
        rule.onNodeWithText("Note 2").performTouchInput { swipeRight() }
        rule.waitForIdle()

        assertEquals(listOf("swipe:1", "swipe:2"), calls)
    }

    @Test
    fun aLongPressOpensTheMenuOfThatNote() {
        show(stateWith(note(1), note(2)))

        rule.onNodeWithText("Note 2").performTouchInput { longClick() }

        rule.onNodeWithText(text(R.string.notes_action_pin)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.notes_action_archive)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.notes_action_trash)).assertIsDisplayed()
        rule.onAllNodesWithText(text(R.string.notes_action_retry)).assertCountEquals(0)
    }

    @Test
    fun theMenuPinsAndUnpins() {
        show(stateWith(note(1), note(2, pinned = true)))

        rule.onNodeWithText("Note 1").performTouchInput { longClick() }
        rule.onNodeWithText(text(R.string.notes_action_pin)).performClick()
        rule.onNodeWithText("Note 2").performTouchInput { longClick() }
        rule.onNodeWithText(text(R.string.notes_action_unpin)).performClick()

        assertEquals(listOf("pin:1:true", "pin:2:false"), calls)
    }

    @Test
    fun theMenuArchives() {
        show(stateWith(note(1)))

        rule.onNodeWithText("Note 1").performTouchInput { longClick() }
        rule.onNodeWithText(text(R.string.notes_action_archive)).performClick()

        assertEquals(listOf("archive:1"), calls)
    }

    @Test
    fun movingToTheTrashFromTheMenuAsksFirst() {
        show(stateWith(note(1)))

        rule.onNodeWithText("Note 1").performTouchInput { longClick() }
        rule.onNodeWithText(text(R.string.notes_action_trash)).performClick()

        rule.onNodeWithText(text(R.string.notes_trash_confirm_title)).assertIsDisplayed()
        assertTrue("nothing happens before the user confirms", calls.isEmpty())

        rule.onNodeWithText(text(R.string.notes_action_trash)).performClick()

        assertEquals(listOf("trash:1"), calls)
    }

    @Test
    fun cancellingTheTrashConfirmationDoesNothing() {
        show(stateWith(note(1)))
        rule.onNodeWithText("Note 1").performTouchInput { longClick() }
        rule.onNodeWithText(text(R.string.notes_action_trash)).performClick()

        rule.onNodeWithText(text(R.string.notes_confirm_cancel)).performClick()

        assertTrue(calls.isEmpty())
        rule.onAllNodesWithText(text(R.string.notes_trash_confirm_title)).assertCountEquals(0)
    }

    @Test
    fun aFailedNoteCanBeRetriedOrDiscardedAfterConfirming() {
        show(stateWith(note(1, syncState = SyncState.Failed)))

        rule.onNodeWithText("Note 1").performTouchInput { longClick() }
        rule.onNodeWithText(text(R.string.notes_action_retry)).performClick()
        assertEquals(listOf("retry:1"), calls)

        rule.onNodeWithText("Note 1").performTouchInput { longClick() }
        rule.onNodeWithText(text(R.string.notes_action_discard)).performClick()
        rule.onNodeWithText(text(R.string.notes_discard_confirm_title)).assertIsDisplayed()
        assertEquals(listOf("retry:1"), calls)

        rule.onNodeWithText(text(R.string.notes_action_discard)).performClick()
        assertEquals(listOf("retry:1", "discard:1"), calls)
    }

    // ---------------------------- screen reader actions ----------------------------

    private fun customActionLabels(title: String) =
        rule
            .onNodeWithText(title)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsActions.CustomActions)
            .orEmpty()
            .map { it.label }

    private fun runCustomAction(
        title: String,
        label: String,
    ) {
        val action =
            rule
                .onNodeWithText(title)
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .single { it.label == label }
        rule.runOnUiThread { action.action() }
        rule.waitForIdle()
    }

    @Test
    fun everyGestureHasAScreenReaderAction() {
        show(stateWith(note(1), note(2, syncState = SyncState.Failed)))

        assertEquals(
            listOf(
                text(R.string.notes_action_trash),
                text(R.string.notes_action_pin),
                text(R.string.notes_action_archive),
            ),
            customActionLabels("Note 1"),
        )
        assertEquals(
            listOf(
                text(R.string.notes_action_trash),
                text(R.string.notes_action_pin),
                text(R.string.notes_action_archive),
                text(R.string.notes_action_retry),
                text(R.string.notes_action_discard),
            ),
            customActionLabels("Note 2"),
        )
    }

    @Test
    fun theScreenReaderActionsDoWhatTheGesturesDo() {
        show(stateWith(note(1), note(2, pinned = true, syncState = SyncState.Failed)))

        runCustomAction("Note 1", text(R.string.notes_action_trash))
        runCustomAction("Note 1", text(R.string.notes_action_pin))
        runCustomAction("Note 1", text(R.string.notes_action_archive))
        runCustomAction("Note 2", text(R.string.notes_action_unpin))
        runCustomAction("Note 2", text(R.string.notes_action_retry))

        assertEquals(listOf("swipe:1", "pin:1:true", "archive:1", "pin:2:false", "retry:2"), calls)
    }

    @Test
    fun discardingFromTheScreenReaderAlsoAsksFirst() {
        show(stateWith(note(1, syncState = SyncState.Failed)))

        runCustomAction("Note 1", text(R.string.notes_action_discard))

        rule.onNodeWithText(text(R.string.notes_discard_confirm_title)).assertIsDisplayed()
        assertTrue(calls.isEmpty())
    }

    // ---------------------------- snackbars ----------------------------

    @Test
    fun aSwipedNoteOffersUndoUntilTheViewModelEndsThePeriod() {
        show(stateWith(note(2)) { it.copy(pendingDelete = PendingDelete(1)) })

        rule.onNodeWithText(text(R.string.notes_trashed)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.notes_undo)).performClick()

        assertEquals(listOf("undo"), calls)
    }

    @Test
    fun theUndoSnackbarDisappearsWhenThePeriodIsOver() {
        show(stateWith(note(2)) { it.copy(pendingDelete = PendingDelete(1)) })
        rule.onNodeWithText(text(R.string.notes_trashed)).assertIsDisplayed()

        rule.runOnUiThread { state = state.copy(pendingDelete = null) }
        rule.waitForIdle()

        rule.onAllNodesWithText(text(R.string.notes_trashed)).assertCountEquals(0)
    }

    @Test
    fun aFailedActionIsExplainedOnceAndThenReportedAsShown() {
        show(stateWith(note(1)) { it.copy(message = NoteListMessage.ActionFailed) })

        rule.onNodeWithText(text(R.string.notes_action_failed)).assertIsDisplayed()
        rule.waitUntil(WAIT_MS) { calls.contains("messageShown") }

        assertEquals(1, calls.count { it == "messageShown" })
    }

    // ---------------------------- the top of the list ----------------------------

    private fun manyNotes(
        count: Int,
        firstId: Long = 1,
    ) = (0 until count).map { note(firstId + it) }.toTypedArray()

    @Test
    fun aNewNoteAtTheTopIsShownWhenTheUserWasAlreadyAtTheTop() {
        show(stateWith(*manyNotes(30, firstId = 10)))
        rule.onNodeWithText("Note 10").assertIsDisplayed()

        rule.runOnUiThread { state = stateWith(note(1), *manyNotes(30, firstId = 10)) }
        rule.waitForIdle()

        rule.onNodeWithText("Note 1").assertIsDisplayed()
    }

    @Test
    fun aUserWhoScrolledDownIsNotPulledBackToTheTop() {
        show(stateWith(*manyNotes(30, firstId = 10)))
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToIndex(15)
        rule.waitForIdle()
        rule.onAllNodesWithText("Note 10").assertCountEquals(0)

        rule.runOnUiThread { state = stateWith(note(1), *manyNotes(30, firstId = 10)) }
        rule.waitForIdle()

        rule.onAllNodesWithText("Note 1").assertCountEquals(0)
        rule.onAllNodesWithText("Note 10").assertCountEquals(0)
    }

    // ---------------------------- large text ----------------------------

    @Test
    fun theScreenWorksAtDoubleTheFontSize() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                WritaTheme {
                    NotesScreen(
                        stateWith(
                            note(
                                1,
                                title = "A fairly long note title that has to wrap or be cut",
                                tags = listOf("one", "two"),
                            ),
                        ),
                        actions,
                        navigation,
                    )
                }
            }
        }

        rule.onNodeWithText(text(R.string.app_name)).assertIsDisplayed()
        rule.onNodeWithContentDescription(text(R.string.notes_add_note)).assertIsDisplayed().performClick()
        rule.onNodeWithText("A fairly long note title", substring = true).performClick()

        assertEquals(listOf("create", "open:1"), calls)
    }

    @Test
    fun theIconsHaveDescriptions() {
        show()

        rule.onAllNodesWithContentDescription(text(R.string.notes_menu_search)).onFirst().assertIsDisplayed()
        rule.onAllNodesWithContentDescription(text(R.string.notes_menu_more)).onFirst().assertIsDisplayed()
        rule.onAllNodesWithContentDescription(text(R.string.notes_add_note)).onFirst().assertIsDisplayed()
    }

    private companion object {
        const val WAIT_MS = 10_000L
    }
}
