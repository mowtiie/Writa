package com.mowtiie.writa.feature.auth

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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

class LoginScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(id: Int) = context.getString(id)

    private fun text(
        id: Int,
        vararg args: Any,
    ) = context.getString(id, *args)

    private var state by mutableStateOf(LoginUiState(serverHost = "abc.supabase.co"))
    private var signIns = 0
    private var signUps = 0
    private var serverChanges = 0
    private var shown = 0
    private val events = Channel<LoginEvent>(Channel.BUFFERED)

    private fun show(initial: LoginUiState = LoginUiState(serverHost = "abc.supabase.co")) {
        state = initial
        rule.setContent {
            WritaTheme {
                LoginScreen(
                    state = state,
                    onEmailChange = { state = state.copy(email = it) },
                    onPasswordChange = { state = state.copy(password = it) },
                    onSignIn = { signIns++ },
                    onCreateAccount = { signUps++ },
                    onChangeServer = { serverChanges++ },
                    onMessageShown = {
                        shown++
                        state = state.copy(message = null)
                    },
                    events = events.receiveAsFlow(),
                )
            }
        }
    }

    private val isTextField = SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)
    private val hasError = SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)
    private val hasPassword = SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)

    private fun emailField() = rule.onAllNodes(isTextField)[0]

    private fun passwordField() = rule.onAllNodes(isTextField)[1]

    // ---------------------------- content ----------------------------

    @Test
    fun theScreenShowsTheAppTheSloganAndBothWaysIn() {
        show()

        rule.onNodeWithText(text(R.string.app_name)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.app_slogan)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.login_sign_in)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.login_create_account)).assertIsDisplayed()
    }

    @Test
    fun theTitleIsMarkedAsAHeading() {
        show()

        rule.onNodeWithText(text(R.string.app_name)).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun theServerThisLoginGoesToIsShown() {
        show()

        rule.onNodeWithText(text(R.string.login_server, "abc.supabase.co")).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.login_change_server)).assertIsDisplayed()
    }

    @Test
    fun withoutAServerNoServerLineIsShown() {
        show(LoginUiState(serverHost = null))

        rule.onAllNodesWithText(text(R.string.login_server, "abc.supabase.co")).assertCountEquals(0)
        rule.onAllNodes(hasText("Server:", substring = true)).assertCountEquals(0)
    }

    @Test
    fun theFieldsAreMarkedForAutofill() {
        show()

        assertEquals(
            ContentType.EmailAddress,
            emailField().fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentType),
        )
        assertEquals(
            ContentType.Password,
            passwordField().fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentType),
        )
    }

    @Test
    fun theEmailCanBeClearedOnceItHasText() {
        show()
        rule.onAllNodesWithContentDescription(text(R.string.field_clear)).assertCountEquals(0)

        emailField().performTextInput("me@example.com")
        rule.onNodeWithContentDescription(text(R.string.field_clear)).performClick()

        assertEquals("", state.email)
        rule.onAllNodesWithContentDescription(text(R.string.field_clear)).assertCountEquals(0)
    }

    // ---------------------------- input ----------------------------

    @Test
    fun typingReachesTheState() {
        show()

        emailField().performTextInput("me@example.com")
        passwordField().performTextInput("secret")

        assertEquals("me@example.com", state.email)
        assertEquals("secret", state.password)
    }

    @Test
    fun theButtonsDoWhatTheySay() {
        show()

        rule.onNodeWithText(text(R.string.login_sign_in)).performClick()
        rule.onNodeWithText(text(R.string.login_create_account)).performClick()
        rule.onNodeWithText(text(R.string.login_change_server)).performClick()

        assertEquals(1, signIns)
        assertEquals(1, signUps)
        assertEquals(1, serverChanges)
    }

    @Test
    fun theKeyboardDoneKeyInThePasswordSignsIn() {
        show()

        passwordField().performImeAction()

        assertEquals(1, signIns)
        assertEquals(0, signUps)
    }

    @Test
    fun aHardwareEnterKeyInThePasswordSignsIn() {
        show()

        passwordField().requestFocus()
        passwordField().performKeyInput { pressKey(Key.Enter) }

        assertTrue(signIns >= 1)
        assertEquals(0, signUps)
    }

    @Test
    fun theKeyboardNextKeyMovesFromTheEmailToThePassword() {
        show()

        emailField().performImeAction()

        passwordField().assertIsFocused()
        assertEquals(0, signIns)
    }

    @Test
    fun focusFollowsTheEventsOfTheViewModel() {
        show()

        rule.runOnUiThread { events.trySend(LoginEvent.FocusPassword) }
        rule.waitForIdle()
        passwordField().assertIsFocused()

        rule.runOnUiThread { events.trySend(LoginEvent.FocusEmail) }
        rule.waitForIdle()
        emailField().assertIsFocused()
    }

    // ---------------------------- the password ----------------------------

    @Test
    fun thePasswordIsHiddenUntilTheUserAsksToSeeIt() {
        show(LoginUiState(password = "secret", serverHost = "h"))

        assertFalse("hidden at first", displayedPassword().contains("secret"))
        rule.onNodeWithContentDescription(text(R.string.login_show_password)).performClick()

        assertEquals("secret", displayedPassword())
        rule.onNodeWithContentDescription(text(R.string.login_hide_password)).assertIsDisplayed()

        rule.onNodeWithContentDescription(text(R.string.login_hide_password)).performClick()

        assertFalse("hidden again", displayedPassword().contains("secret"))
        rule.onNodeWithContentDescription(text(R.string.login_show_password)).assertIsDisplayed()
    }

    @Test
    fun thePasswordKeepsTheKeyboardOfAPasswordSoItIsNotLearnedAsAWord() {
        show(LoginUiState(password = "secret", serverHost = "h"))

        passwordField().assert(hasPassword)
        rule.onNodeWithContentDescription(text(R.string.login_show_password)).performClick()
        passwordField().assert(hasPassword)
    }

    /** What the field draws: dots while hidden, the real text once shown. */
    private fun displayedPassword(): String {
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        passwordField()
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(results)
        return results
            .single()
            .layoutInput.text.text
    }

    // ---------------------------- errors, messages and loading ----------------------------

    @Test
    fun emailProblemsShowOnTheEmailFieldAsTextAndAsASemanticError() {
        val expected =
            mapOf(
                EmailProblem.Required to text(R.string.error_field_required),
                EmailProblem.Invalid to text(R.string.error_email_invalid),
            )
        show()

        for ((problem, message) in expected) {
            rule.runOnUiThread { state = LoginUiState(emailProblem = problem, serverHost = "h") }
            rule.onNodeWithText(message).assertIsDisplayed()
            emailField().assert(hasError)
            passwordField().assert(!hasError)
        }
    }

    @Test
    fun aMissingPasswordShowsOnThePasswordField() {
        show(LoginUiState(passwordProblem = PasswordProblem.Required, serverHost = "h"))

        rule.onNodeWithText(text(R.string.error_field_required)).assertIsDisplayed()
        passwordField().assert(hasError)
        emailField().assert(!hasError)
    }

    @Test
    fun everyMessageIsShownOnceAndThenReportedAsShown() {
        val expected =
            mapOf(
                LoginMessage.FromServer("Invalid login credentials") to "Invalid login credentials",
                LoginMessage.ServerUnreachable to text(R.string.error_server_unreachable),
                LoginMessage.SecureConnectionProblem to text(R.string.error_secure_connection),
                LoginMessage.ConnectionNotSetUp to text(R.string.error_connection_not_set_up),
                LoginMessage.ConfirmEmail to text(R.string.message_confirm_email),
                LoginMessage.Generic to text(R.string.error_generic_try_again),
            )
        show()

        for ((message, shownText) in expected) {
            val before = shown
            rule.runOnUiThread { state = state.copy(message = message) }
            rule.waitUntil(WAIT_MS) { rule.onAllNodes(hasText(shownText)).fetchSemanticsNodes().isNotEmpty() }
            rule.waitUntil(WAIT_MS) { shown == before + 1 }
        }
        assertEquals(expected.size, shown)
    }

    @Test
    fun whileWaitingTheFormIsLockedAndProgressIsShown() {
        show(LoginUiState(email = "me@example.com", password = "x", isLoading = true, serverHost = "h"))

        rule.onNodeWithText(text(R.string.login_sign_in)).assertIsNotEnabled()
        rule.onNodeWithText(text(R.string.login_create_account)).assertIsNotEnabled()
        rule.onNodeWithText(text(R.string.login_change_server)).assertIsNotEnabled()
        emailField().assertIsNotEnabled()
        passwordField().assertIsNotEnabled()
        rule.onNode(hasContentDescription(text(R.string.login_working))).assertIsDisplayed()
    }

    @Test
    fun whenIdleEverythingIsEnabledAndThereIsNoProgress() {
        show()

        rule.onNodeWithText(text(R.string.login_sign_in)).assertIsEnabled()
        rule.onNodeWithText(text(R.string.login_create_account)).assertIsEnabled()
        rule.onNodeWithText(text(R.string.login_change_server)).assertIsEnabled()
        rule.onAllNodes(hasContentDescription(text(R.string.login_working))).assertCountEquals(0)
    }

    // ---------------------------- accessibility and safety ----------------------------

    @Test
    fun theTitleShrinksToStayOnOneLineWhenThereIsLittleRoomAtDoubleTheFontSize() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                WritaTheme {
                    Box(Modifier.width(NARROW_WIDTH)) { LoginScreen(LoginUiState(), {}, {}, {}, {}, {}, {}) }
                }
            }
        }

        val results = mutableListOf<TextLayoutResult>()
        rule
            .onNodeWithText(text(R.string.app_name))
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(results)

        val layout = results.single()
        assertEquals("the title must stay on one line, not break inside the word", 1, layout.lineCount)
        assertFalse("the title must not be cut off", layout.didOverflowWidth)
        assertFalse(layout.hasVisualOverflow)
    }

    @Test
    fun everythingStaysReachableAtDoubleTheFontSize() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                WritaTheme {
                    LoginScreen(
                        LoginUiState(serverHost = "h"),
                        {},
                        {},
                        { signIns++ },
                        { signUps++ },
                        { serverChanges++ },
                        {},
                    )
                }
            }
        }

        rule.onNodeWithText(text(R.string.login_sign_in)).performScrollTo().performClick()
        rule.onNodeWithText(text(R.string.login_create_account)).performScrollTo().performClick()
        rule.onNodeWithText(text(R.string.login_change_server)).performScrollTo().performClick()

        assertEquals(listOf(1, 1, 1), listOf(signIns, signUps, serverChanges))
    }

    @Test
    fun touchesThroughAnotherWindowAreIgnoredWhileTheScreenIsShown() {
        var view: View? = null
        var visible by mutableStateOf(true)
        rule.setContent {
            view = LocalView.current
            if (visible) LoginScreen(LoginUiState(), {}, {}, {}, {}, {}, {})
        }
        rule.waitForIdle()

        assertTrue(view!!.filterTouchesWhenObscured)

        rule.runOnUiThread { visible = false }
        rule.waitForIdle()

        assertFalse("the filter must not outlive the screen", view!!.filterTouchesWhenObscured)
    }

    private companion object {
        const val WAIT_MS = 10_000L
        val NARROW_WIDTH = 200.dp
    }
}
