package com.mowtiie.writa.feature.setup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.mowtiie.writa.core.datastore.Connection
import com.mowtiie.writa.core.datastore.ConnectionStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private class FakeProbe(
    var result: ProbeResult = ProbeResult.Reachable,
) : ServerProbe {
    val calls = mutableListOf<Pair<String, String>>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun check(
        baseUrl: String,
        key: String,
    ): ProbeResult {
        calls += baseUrl to key
        gate?.await()
        return result
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val probe = FakeProbe()

    @Before
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    private fun TestScope.connectionStore() =
        ConnectionStore(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { File(folder.root, "connection.preferences_pb") },
            ),
        )

    private fun TestScope.viewModel(
        saved: SavedStateHandle = SavedStateHandle(),
        defaults: SetupDefaults = SetupDefaults("", ""),
        store: ConnectionStore = connectionStore(),
    ) = SetupViewModel(saved, defaults, probe, store)

    private fun SetupViewModel.fill(
        url: String = "https://abc.supabase.co",
        key: String = "anon-key",
    ) {
        onUrlChange(url)
        onKeyChange(key)
    }

    /** Waits for the save, which finishes on a real I/O thread that the test scheduler does not control. */
    private suspend fun ConnectionStore.saved(): Connection = connection.filterNotNull().first()

    // ---------------------------- the fields ----------------------------

    @Test
    fun theFieldsStartEmptyInAReleaseBuild() =
        runTest {
            assertEquals(SetupUiState(), viewModel().state.value)
        }

    @Test
    fun aDebugBuildPreFillsTheFieldsFromTheDefaults() =
        runTest {
            val state = viewModel(defaults = SetupDefaults("https://dev.supabase.co", "dev-key")).state.value

            assertEquals("https://dev.supabase.co", state.url)
            assertEquals("dev-key", state.key)
        }

    @Test
    fun whatTheUserTypedSurvivesTheProcessBeingKilled() =
        runTest {
            val first = SavedStateHandle()
            viewModel(first, SetupDefaults("d-url", "d-key")).fill("https://typed.example", "typed-key")

            val restored = viewModel(first, SetupDefaults("d-url", "d-key")).state.value

            assertEquals("https://typed.example", restored.url)
            assertEquals("typed-key", restored.key)
        }

    @Test
    fun typingClearsOnlyTheErrorOfThatField() =
        runTest {
            val viewModel = viewModel()
            viewModel.submit()
            assertEquals(UrlProblem.Required, viewModel.state.value.urlProblem)
            assertEquals(KeyProblem.Required, viewModel.state.value.keyProblem)

            viewModel.onUrlChange("h")

            assertNull(viewModel.state.value.urlProblem)
            assertEquals(KeyProblem.Required, viewModel.state.value.keyProblem)
        }

    // ---------------------------- checking before asking the server ----------------------------

    @Test
    fun badInputIsReportedOnBothFieldsWithoutAskingTheServer() =
        runTest {
            val viewModel = viewModel()
            viewModel.fill(url = "http://abc.supabase.co", key = "has space")

            viewModel.submit()

            assertEquals(UrlProblem.SchemeRequired, viewModel.state.value.urlProblem)
            assertEquals(KeyProblem.Invalid, viewModel.state.value.keyProblem)
            assertFalse(viewModel.state.value.isLoading)
            assertTrue(probe.calls.isEmpty())
        }

    @Test
    fun focusGoesToTheFirstFieldThatIsWrong() =
        runTest {
            val viewModel = viewModel()

            viewModel.events.test {
                viewModel.fill(url = "", key = "")
                viewModel.submit()
                assertEquals(SetupEvent.FocusUrl, awaitItem())

                viewModel.fill(url = "https://abc.supabase.co", key = "")
                viewModel.submit()
                assertEquals(SetupEvent.FocusKey, awaitItem())
            }
        }

    // ---------------------------- asking the server ----------------------------

    @Test
    fun aWorkingServerIsSavedTrimmedAndWithoutTheTrailingSlash() =
        runTest {
            val store = connectionStore()
            val viewModel = viewModel(store = store)
            viewModel.fill(url = "  https://abc.supabase.co/ ", key = " anon-key\n")

            viewModel.submit()
            advanceUntilIdle()

            assertEquals(listOf("https://abc.supabase.co" to "anon-key"), probe.calls)
            assertEquals(Connection("https://abc.supabase.co", "anon-key"), store.saved())
            assertFalse(viewModel.state.value.isLoading)
        }

    @Test
    fun nothingIsSavedUntilTheServerHasAnswered() =
        runTest {
            val store = connectionStore()
            val viewModel = viewModel(store = store)
            probe.gate = CompletableDeferred()
            viewModel.fill()

            viewModel.submit()

            assertTrue(viewModel.state.value.isLoading)
            assertNull(store.current())

            probe.gate?.complete(Unit)
            advanceUntilIdle()
            assertEquals("https://abc.supabase.co", store.saved().baseUrl)
        }

    @Test
    fun submittingAgainWhileCheckingDoesNothing() =
        runTest {
            val viewModel = viewModel()
            probe.gate = CompletableDeferred()
            viewModel.fill()

            viewModel.submit()
            viewModel.submit()
            viewModel.submit()

            assertEquals(1, probe.calls.size)
        }

    @Test
    fun aServerThatCannotBeReachedIsReportedOnTheUrl() =
        runTest {
            val store = connectionStore()
            val viewModel = viewModel(store = store)
            probe.result = ProbeResult.NotReached
            viewModel.fill()

            viewModel.events.test {
                viewModel.submit()

                assertEquals(UrlProblem.NotReached, viewModel.state.value.urlProblem)
                assertNull(viewModel.state.value.keyProblem)
                assertFalse(viewModel.state.value.isLoading)
                assertEquals(SetupEvent.FocusUrl, awaitItem())
            }
            assertNull(store.current())
        }

    @Test
    fun urlProblemsFromTheServerAreReportedOnTheUrl() =
        runTest {
            val viewModel = viewModel()
            viewModel.fill()
            val expected =
                mapOf(
                    ProbeResult.SecureConnectionProblem to UrlProblem.SecureConnection,
                    ProbeResult.UnexpectedResponse(404) to UrlProblem.UnexpectedResponse(404),
                    ProbeResult.InvalidUrl to UrlProblem.Invalid,
                )

            for ((result, problem) in expected) {
                probe.result = result
                viewModel.submit()
                assertEquals(problem, viewModel.state.value.urlProblem)
            }
        }

    @Test
    fun aRejectedKeyIsReportedOnTheKeyAndTheUrlIsLeftAlone() =
        runTest {
            val store = connectionStore()
            val viewModel = viewModel(store = store)
            probe.result = ProbeResult.KeyRejected(401)
            viewModel.fill()

            viewModel.events.test {
                viewModel.submit()

                assertEquals(KeyProblem.Rejected(401), viewModel.state.value.keyProblem)
                assertNull(viewModel.state.value.urlProblem)
                assertEquals(SetupEvent.FocusKey, awaitItem())
            }
            assertNull(store.current())
        }

    @Test
    fun afterAFailureTheUserCanFixTheInputAndTryAgain() =
        runTest {
            val store = connectionStore()
            val viewModel = viewModel(store = store)
            probe.result = ProbeResult.NotReached
            viewModel.fill()
            viewModel.submit()

            probe.result = ProbeResult.Reachable
            viewModel.onUrlChange("https://fixed.supabase.co")
            viewModel.submit()
            advanceUntilIdle()

            assertEquals("https://fixed.supabase.co", store.saved().baseUrl)
            assertNull(viewModel.state.value.urlProblem)
        }

    @Test
    fun aPreviousFailureIsClearedWhenCheckingAgain() =
        runTest {
            val viewModel = viewModel()
            probe.result = ProbeResult.KeyRejected(403)
            viewModel.fill()
            viewModel.submit()
            assertEquals(KeyProblem.Rejected(403), viewModel.state.value.keyProblem)

            probe.gate = CompletableDeferred()
            viewModel.submit()

            assertNull(viewModel.state.value.keyProblem)
            assertTrue(viewModel.state.value.isLoading)
        }
}
