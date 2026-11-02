package com.mowtiie.writa.feature.setup

import android.content.Context
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.mowtiie.writa.R
import com.mowtiie.writa.core.ui.theme.WritaTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SetupScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(id: Int) = context.getString(id)

    private fun text(
        id: Int,
        vararg args: Any,
    ) = context.getString(id, *args)

    private var state by mutableStateOf(SetupUiState())
    private var submits = 0
    private val events = Channel<SetupEvent>(Channel.BUFFERED)

    private fun show(initial: SetupUiState = SetupUiState()) {
        state = initial
        rule.setContent {
            WritaTheme {
                SetupScreen(
                    state = state,
                    onUrlChange = { state = state.copy(url = it) },
                    onKeyChange = { state = state.copy(key = it) },
                    onSubmit = { submits++ },
                    events = events.receiveAsFlow(),
                )
            }
        }
    }

    private fun urlField() = rule.onAllNodes(isTextField)[0]

    private fun keyField() = rule.onAllNodes(isTextField)[1]

    private val isTextField = SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)

    private val hasError = SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)

    // ---------------------------- content ----------------------------

    @Test
    fun theScreenExplainsWhatToEnter() {
        show()

        rule.onNodeWithText(text(R.string.setup_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.setup_description)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.setup_connect)).assertIsDisplayed()
    }

    @Test
    fun theTitleIsMarkedAsAHeading() {
        show()

        rule
            .onNodeWithText(
                text(R.string.setup_title),
            ).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun theFieldsAreLabelled() {
        show()

        rule.onAllNodesWithText(text(R.string.setup_url_label), useUnmergedTree = true)[0].assertIsDisplayed()
        rule.onAllNodesWithText(text(R.string.setup_key_label), useUnmergedTree = true)[0].assertIsDisplayed()
    }

    @Test
    fun theFieldsStartWithTheGivenValues() {
        show(SetupUiState(url = "https://dev.supabase.co", key = "dev-key"))

        urlField().assert(hasText("https://dev.supabase.co", substring = true))
        keyField().assert(hasText("dev-key", substring = true))
    }

    @Test
    fun theUrlFieldShowsAnExampleUntilThereIsAProblem() {
        show()

        rule.onNodeWithText(text(R.string.setup_url_helper)).assertIsDisplayed()

        state = state.copy(urlProblem = UrlProblem.Invalid)

        rule.onAllNodesWithText(text(R.string.setup_url_helper)).assertCountEquals(0)
        rule.onNodeWithText(text(R.string.error_server_url_invalid)).assertIsDisplayed()
    }

    @Test
    fun eachFieldCanBeClearedOnceItHasText() {
        show(SetupUiState(url = "https://dev.supabase.co", key = "dev-key"))

        rule.onAllNodesWithContentDescription(text(R.string.field_clear)).assertCountEquals(2)
        rule.onAllNodesWithContentDescription(text(R.string.field_clear))[0].performClick()
        rule.onAllNodesWithContentDescription(text(R.string.field_clear))[0].performClick()

        assertEquals("", state.url)
        assertEquals("", state.key)
        rule.onAllNodesWithContentDescription(text(R.string.field_clear)).assertCountEquals(0)
    }

    // ---------------------------- input ----------------------------

    @Test
    fun typingReachesTheStateOfBothFields() {
        show()

        urlField().performTextInput("https://abc.supabase.co")
        keyField().performTextInput("anon-key")

        assertEquals("https://abc.supabase.co", state.url)
        assertEquals("anon-key", state.key)
    }

    @Test
    fun theConnectButtonSubmits() {
        show()

        rule.onNodeWithText(text(R.string.setup_connect)).performClick()

        assertEquals(1, submits)
    }

    @Test
    fun theKeyboardDoneKeySubmits() {
        show()

        keyField().performImeAction()

        assertEquals(1, submits)
    }

    @Test
    fun aHardwareEnterKeySubmits() {
        show()

        keyField().requestFocus()
        keyField().performKeyInput { pressKey(Key.Enter) }

        assertTrue("Enter on a hardware keyboard must submit", submits >= 1)
    }

    @Test
    fun theKeyboardNextKeyMovesFromTheUrlToTheKey() {
        show()

        urlField().performImeAction()

        keyField().assertIsFocused()
        assertEquals(0, submits)
    }

    @Test
    fun focusFollowsTheEventsOfTheViewModel() {
        show()

        rule.runOnUiThread { events.trySend(SetupEvent.FocusKey) }
        rule.waitForIdle()
        keyField().assertIsFocused()

        rule.runOnUiThread { events.trySend(SetupEvent.FocusUrl) }
        rule.waitForIdle()
        urlField().assertIsFocused()
    }

    // ---------------------------- errors and loading ----------------------------

    @Test
    fun urlProblemsShowOnTheUrlFieldAsTextAndAsASemanticError() {
        show(SetupUiState(urlProblem = UrlProblem.SchemeRequired))

        rule.onNodeWithText(text(R.string.error_server_url_scheme_required)).assertIsDisplayed()
        urlField().assert(hasError)
        keyField().assert(!hasError)
    }

    @Test
    fun everyUrlProblemHasItsOwnMessage() {
        val expected =
            mapOf(
                UrlProblem.Required to text(R.string.error_field_required),
                UrlProblem.SchemeRequired to text(R.string.error_server_url_scheme_required),
                UrlProblem.Invalid to text(R.string.error_server_url_invalid),
                UrlProblem.NotReached to text(R.string.error_server_not_reached),
                UrlProblem.SecureConnection to text(R.string.error_secure_connection),
                UrlProblem.UnexpectedResponse(404) to text(R.string.error_server_unexpected_response, 404),
            )
        show()

        for ((problem, message) in expected) {
            rule.runOnUiThread { state = SetupUiState(urlProblem = problem) }
            rule.onNodeWithText(message).assertIsDisplayed()
        }
    }

    @Test
    fun keyProblemsShowOnTheKeyField() {
        val expected =
            mapOf(
                KeyProblem.Required to text(R.string.error_field_required),
                KeyProblem.Invalid to text(R.string.error_key_invalid),
                KeyProblem.Rejected(401) to text(R.string.error_key_rejected, 401),
            )
        show()

        for ((problem, message) in expected) {
            rule.runOnUiThread { state = SetupUiState(keyProblem = problem) }
            rule.onNodeWithText(message).assertIsDisplayed()
            keyField().assert(hasError)
            urlField().assert(!hasError)
        }
    }

    @Test
    fun noErrorIsShownWhenThereIsNoProblem() {
        show()

        urlField().assert(!hasError)
        keyField().assert(!hasError)
    }

    @Test
    fun whileCheckingTheFormIsLockedAndProgressIsShown() {
        show(SetupUiState(url = "https://abc.supabase.co", key = "k", isLoading = true))

        rule.onNodeWithText(text(R.string.setup_connect)).assertIsNotEnabled()
        urlField().assertIsNotEnabled()
        keyField().assertIsNotEnabled()
        rule.onNode(hasContentDescription(text(R.string.setup_connecting))).assertIsDisplayed()
    }

    @Test
    fun whenIdleTheFormIsEnabledAndThereIsNoProgress() {
        show()

        rule.onNodeWithText(text(R.string.setup_connect)).assertIsEnabled()
        rule.onAllNodes(hasContentDescription(text(R.string.setup_connecting))).assertCountEquals(0)
    }

    // ---------------------------- accessibility and safety ----------------------------

    @Test
    fun everythingStaysReachableAtDoubleTheFontSize() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                WritaTheme {
                    SetupScreen(state = SetupUiState(), onUrlChange = {}, onKeyChange = {}, onSubmit = { submits++ })
                }
            }
        }

        rule.onNodeWithText(text(R.string.setup_title)).assertIsDisplayed()
        rule
            .onNodeWithText(text(R.string.setup_connect))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, submits)
    }

    @Test
    fun touchesThroughAnotherWindowAreIgnoredWhileTheScreenIsShown() {
        var view: View? = null
        var visible by mutableStateOf(true)
        rule.setContent {
            view = LocalView.current
            if (visible) SetupScreen(SetupUiState(), {}, {}, {})
        }
        rule.waitForIdle()

        assertTrue(view!!.filterTouchesWhenObscured)

        rule.runOnUiThread { visible = false }
        rule.waitForIdle()

        assertFalse("the filter must not outlive the screen", view!!.filterTouchesWhenObscured)
    }
}
