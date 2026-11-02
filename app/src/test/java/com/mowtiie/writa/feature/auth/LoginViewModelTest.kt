package com.mowtiie.writa.feature.auth

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.mowtiie.writa.core.network.HttpClients
import com.mowtiie.writa.core.network.TestBackend
import com.mowtiie.writa.core.network.apiJson
import com.mowtiie.writa.core.security.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob())
    private lateinit var backend: TestBackend

    private val server get() = backend.server

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        backend = TestBackend(folder.root).also { it.start() }
        runBlocking { backend.sessionStore.clear() }
    }

    @After
    fun tearDown() {
        backend.close()
        scope.cancel()
        Dispatchers.resetMain()
    }

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        LoginViewModel(
            savedState = saved,
            auth =
                AuthRepository(
                    backend.connectionStore,
                    backend.sessionStore,
                    HttpClients.plain(),
                    apiJson,
                    Dispatchers.IO,
                    scope,
                ),
            connectionStore = backend.connectionStore,
        )

    private fun LoginViewModel.fill(
        email: String = "me@example.com",
        password: String = "secret",
    ) {
        onEmailChange(email)
        onPasswordChange(password)
    }

    private fun LoginViewModel.settled(): LoginUiState =
        runBlocking { withTimeout(WAIT_MS) { state.first { !it.isLoading && it.serverHost != null } } }

    private fun session() = runBlocking { backend.sessionStore.current() }

    private fun tokens(email: String = "me@example.com") =
        MockResponse().setBody("""{"access_token":"a","refresh_token":"r","user":{"email":"$email"}}""")

    // ---------------------------- the form ----------------------------

    @Test
    fun theFormStartsEmptyAndNamesTheServer() {
        val state = viewModel().settled()

        assertEquals("", state.email)
        assertEquals("", state.password)
        assertEquals(server.hostName, state.serverHost)
        assertFalse(state.isLoading)
        assertNull(state.message)
    }

    @Test
    fun theEmailSurvivesTheProcessBeingKilled() {
        val saved = SavedStateHandle()
        viewModel(saved).onEmailChange("me@example.com")

        assertEquals("me@example.com", viewModel(saved).settled().email)
    }

    @Test
    fun thePasswordIsNeverWrittenToTheSavedState() {
        val saved = SavedStateHandle()
        val viewModel = viewModel(saved)

        viewModel.fill(password = "hunter2-very-secret")

        assertEquals(setOf("email"), saved.keys())
        assertFalse(saved.keys().any { saved.get<Any>(it)?.toString()?.contains("hunter2") == true })
        assertEquals("", viewModel(saved).settled().password)
    }

    @Test
    fun theServerIsShownByItsHostAndFollowsTheConnection() {
        val viewModel = viewModel()
        assertEquals(server.hostName, viewModel.settled().serverHost)

        runBlocking { backend.connectionStore.save("https://other.example.org/", "k") }

        assertEquals(
            "other.example.org",
            runBlocking {
                withTimeout(WAIT_MS) {
                    viewModel.state.first {
                        it.serverHost ==
                            "other.example.org"
                    }
                }
            }.serverHost,
        )
    }

    @Test
    fun withoutAConnectionNoServerIsShown() {
        val viewModel = viewModel()
        viewModel.settled()

        runBlocking { backend.connectionStore.clear() }

        assertNull(runBlocking { withTimeout(WAIT_MS) { viewModel.state.first { it.serverHost == null } } }.serverHost)
    }

    // ---------------------------- checking before asking the server ----------------------------

    @Test
    fun anEmptyFormReportsBothFieldsWithoutAskingTheServer() {
        val viewModel = viewModel()

        viewModel.signIn()

        val state = viewModel.state.value
        assertEquals(EmailProblem.Required, state.emailProblem)
        assertEquals(PasswordProblem.Required, state.passwordProblem)
        assertFalse(state.isLoading)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun anImplausibleEmailIsReported() {
        val viewModel = viewModel()
        viewModel.fill(email = "not-an-email")

        viewModel.signUp()

        assertEquals(EmailProblem.Invalid, viewModel.state.value.emailProblem)
        assertNull(viewModel.state.value.passwordProblem)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun focusGoesToTheFirstFieldThatIsWrong() {
        val viewModel = viewModel()

        runBlocking {
            viewModel.events.test {
                viewModel.signIn()
                assertEquals(LoginEvent.FocusEmail, awaitItem())

                viewModel.fill(password = "")
                viewModel.signIn()
                assertEquals(LoginEvent.FocusPassword, awaitItem())
            }
        }
    }

    @Test
    fun typingClearsOnlyTheProblemOfThatField() {
        val viewModel = viewModel()
        viewModel.signIn()

        viewModel.onEmailChange("m")

        assertNull(viewModel.state.value.emailProblem)
        assertEquals(PasswordProblem.Required, viewModel.state.value.passwordProblem)
    }

    // ---------------------------- signing in ----------------------------

    @Test
    fun aSuccessfulSignInStoresTheSessionAndClearsThePassword() {
        server.enqueue(tokens())
        val viewModel = viewModel()
        viewModel.fill()

        viewModel.signIn()

        val state = viewModel.settled()
        assertEquals(Session("a", "r", "me@example.com"), session())
        assertEquals("", state.password)
        assertNull(state.message)
        assertEquals("/auth/v1/token?grant_type=password", server.takeRequest().path)
    }

    @Test
    fun theEmailIsTrimmedButThePasswordIsSentAsTyped() {
        server.enqueue(tokens())
        val viewModel = viewModel()
        viewModel.fill(email = "  me@example.com \n", password = "  pass word  ")

        viewModel.signIn()
        viewModel.settled()

        assertEquals("""{"email":"me@example.com","password":"  pass word  "}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun creatingAnAccountSignsTheUserIn() {
        server.enqueue(tokens())
        val viewModel = viewModel()
        viewModel.fill()

        viewModel.signUp()
        viewModel.settled()

        assertEquals("/auth/v1/signup", server.takeRequest().path)
        assertEquals("a", session()?.accessToken)
    }

    @Test
    fun anAccountThatNeedsConfirmingAsksToCheckTheEmailAndKeepsThePassword() {
        server.enqueue(MockResponse().setBody("""{"id":"u1","email":"me@example.com"}"""))
        val viewModel = viewModel()
        viewModel.fill()

        viewModel.signUp()

        val state = viewModel.settled()
        assertEquals(LoginMessage.ConfirmEmail, state.message)
        assertEquals("secret", state.password)
        assertNull(session())
    }

    // ---------------------------- failures ----------------------------

    @Test
    fun aRejectedLoginShowsTheServersMessageAndKeepsWhatWasTyped() {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody("""{"error_description":"Invalid login credentials"}"""),
        )
        val viewModel = viewModel()
        viewModel.fill()

        viewModel.signIn()

        val state = viewModel.settled()
        assertEquals(LoginMessage.FromServer("Invalid login credentials"), state.message)
        assertEquals("me@example.com", state.email)
        assertEquals("secret", state.password)
        assertFalse(state.isLoading)
        assertNull(session())
    }

    @Test
    fun anAnswerWithoutAMessageIsGeneric() {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>Bad gateway</html>"))
        val viewModel = viewModel()
        viewModel.fill()

        viewModel.signIn()

        assertEquals(LoginMessage.Generic, viewModel.settled().message)
    }

    @Test
    fun anUnreachableServerIsReported() {
        val viewModel = viewModel()
        viewModel.fill()
        server.shutdown()

        viewModel.signIn()

        assertEquals(LoginMessage.ServerUnreachable, viewModel.settled().message)
    }

    @Test
    fun withoutAConnectionTheServerIsNotSetUp() {
        val viewModel = viewModel()
        viewModel.settled()
        viewModel.fill()
        runBlocking { backend.connectionStore.clear() }

        viewModel.signIn()

        assertEquals(
            LoginMessage.ConnectionNotSetUp,
            runBlocking {
                withTimeout(WAIT_MS) {
                    viewModel.state.first {
                        !it.isLoading &&
                            it.message != null
                    }
                }
            }.message,
        )
    }

    @Test
    fun aMessageIsShownOnceAndThenCleared() {
        server.enqueue(MockResponse().setResponseCode(500))
        val viewModel = viewModel()
        viewModel.fill()
        viewModel.signIn()
        assertEquals(LoginMessage.Generic, viewModel.settled().message)

        viewModel.onMessageShown()

        assertNull(runBlocking { withTimeout(WAIT_MS) { viewModel.state.first { it.message == null } } }.message)
    }

    @Test
    fun aNewAttemptClearsTheEarlierMessage() {
        server.enqueue(MockResponse().setResponseCode(500))
        val viewModel = viewModel()
        viewModel.fill()
        viewModel.signIn()
        viewModel.settled()

        server.enqueue(tokens().setHeadersDelay(SLOW_MS, TimeUnit.MILLISECONDS))
        viewModel.signIn()

        assertTrue(viewModel.state.value.isLoading)
        assertNull("the old message must not linger while the new attempt runs", viewModel.state.value.message)
        assertNull(viewModel.settled().message)
    }

    // ---------------------------- while waiting ----------------------------

    @Test
    fun aSecondSubmitWhileWaitingIsIgnored() {
        server.enqueue(tokens().setHeadersDelay(SLOW_MS, TimeUnit.MILLISECONDS))
        val viewModel = viewModel()
        viewModel.fill()

        viewModel.signIn()
        assertTrue(viewModel.state.value.isLoading)
        viewModel.signIn()
        viewModel.signUp()
        viewModel.settled()

        assertEquals(1, server.requestCount)
    }

    // ---------------------------- changing the server ----------------------------

    @Test
    fun changingTheServerForgetsItAndLeavesTheAccountAlone() {
        runBlocking { backend.sessionStore.save(Session("a", "r", "me@example.com")) }
        val viewModel = viewModel()
        viewModel.settled()

        viewModel.changeServer()

        runBlocking { withTimeout(WAIT_MS) { backend.connectionStore.connection.first { it == null } } }
        assertEquals("a", session()?.accessToken)
    }

    private companion object {
        const val WAIT_MS = 10_000L
        const val SLOW_MS = 300L
    }
}
