package com.mowtiie.writa.feature.auth

import com.mowtiie.writa.core.datastore.ConnectionStore
import com.mowtiie.writa.core.datastore.testPreferences
import com.mowtiie.writa.core.network.ApiException
import com.mowtiie.writa.core.network.ConnectionNotConfiguredException
import com.mowtiie.writa.core.network.HttpClients
import com.mowtiie.writa.core.network.NetworkErrors
import com.mowtiie.writa.core.network.apiJson
import com.mowtiie.writa.core.security.FakeSecureStorage
import com.mowtiie.writa.core.security.FakeSecureStorageFactory
import com.mowtiie.writa.core.security.Session
import com.mowtiie.writa.core.security.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class AuthRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val server = MockWebServer()
    private lateinit var connectionStore: ConnectionStore
    private lateinit var sessionStore: SessionStore
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        server.start()
        connectionStore =
            ConnectionStore(
                testPreferences(scope, File(folder.root, "connection.preferences_pb")),
            )
        sessionStore =
            SessionStore(FakeSecureStorageFactory(mutableListOf(FakeSecureStorage())), Dispatchers.Unconfined)
        runBlocking { connectionStore.save(server.url("/").toString(), ANON_KEY) }
        repository =
            AuthRepository(connectionStore, sessionStore, HttpClients.plain(), apiJson, Dispatchers.IO, scope)
    }

    @After
    fun tearDown() {
        server.shutdown()
        scope.cancel()
    }

    private fun currentSession() = runBlocking { sessionStore.current() }

    private fun json(
        body: String,
        code: Int = 200,
    ) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private val tokens = """{"access_token":"a","refresh_token":"r","user":{"email":"me@example.com"}}"""

    @Test
    fun signInUsesThePasswordGrantWithOnlyTheApiKey() =
        runTest {
            server.enqueue(json(tokens))

            repository.signIn("me@example.com", " pass word ")

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/auth/v1/token?grant_type=password", request.path)
            assertEquals(ANON_KEY, request.getHeader("apikey"))
            assertNull(request.getHeader("Authorization"))
            assertEquals("""{"email":"me@example.com","password":" pass word "}""", request.body.readUtf8())
        }

    @Test
    fun aSuccessfulSignInSavesTheSession() =
        runTest {
            server.enqueue(json(tokens))

            repository.signIn("me@example.com", "pw")

            assertEquals(Session("a", "r", "me@example.com"), currentSession())
        }

    @Test
    fun aSessionWithoutAUserKeepsNoEmail() =
        runTest {
            server.enqueue(json("""{"access_token":"a","refresh_token":"r"}"""))

            repository.signIn("me@example.com", "pw")

            assertEquals(Session("a", "r", null), currentSession())
        }

    @Test
    fun signUpUsesTheSignupEndpointAndSignsIn() =
        runTest {
            server.enqueue(json(tokens))

            val result = repository.signUp("me@example.com", "pw")

            assertEquals(SignUpResult.SignedIn, result)
            assertEquals("/auth/v1/signup", server.takeRequest().path)
            assertEquals("a", currentSession()?.accessToken)
        }

    @Test
    fun signUpWithoutATokenAsksToConfirmTheEmail() =
        runTest {
            server.enqueue(json("""{"id":"u1","email":"me@example.com"}"""))

            val result = repository.signUp("me@example.com", "pw")

            assertEquals(SignUpResult.ConfirmationRequired, result)
            assertNull(currentSession())
        }

    @Test
    fun aRejectedLoginCarriesTheServersMessage() =
        runTest {
            server.enqueue(
                json("""{"error":"invalid_grant","error_description":"Invalid login credentials"}""", code = 400),
            )

            val error = runCatching { repository.signIn("me@example.com", "wrong") }.exceptionOrNull()

            assertEquals(400, (error as ApiException).code)
            assertEquals("Invalid login credentials", error.serverMessage)
            assertNull(currentSession())
        }

    @Test
    fun otherErrorShapesAreRead() =
        runTest {
            server.enqueue(json("""{"msg":"User already registered"}""", code = 422))

            val error = runCatching { repository.signUp("me@example.com", "pw") }.exceptionOrNull()

            assertEquals("User already registered", (error as ApiException).serverMessage)
        }

    @Test
    fun anUnreadableErrorBodyHasNoServerMessage() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(502).setBody("<html>Bad gateway</html>"))

            val error = runCatching { repository.signIn("me@example.com", "pw") }.exceptionOrNull()

            assertEquals(502, (error as ApiException).code)
            assertNull(error.serverMessage)
        }

    @Test
    fun anUnreachableServerIsAConnectivityProblem() =
        runTest {
            server.shutdown()

            val error = runCatching { repository.signIn("me@example.com", "pw") }.exceptionOrNull()

            assertTrue(NetworkErrors.isConnectivityProblem(error))
        }

    @Test
    fun withoutAConnectionNothingIsSent() =
        runTest {
            connectionStore.clear()

            assertThrows(ConnectionNotConfiguredException::class.java) {
                runBlocking { repository.signIn("me@example.com", "pw") }
            }
            assertEquals(0, server.requestCount)
        }

    @Test
    fun aSuccessAnswerWithoutTokensIsNotASignIn() =
        runTest {
            server.enqueue(json("""{"id":"u1"}"""))

            assertThrows(IOException::class.java) { runBlocking { repository.signIn("me@example.com", "pw") } }

            assertNull(currentSession())
        }

    @Test
    fun anAccessTokenWithoutARefreshTokenIsNotASession() =
        runTest {
            server.enqueue(json("""{"access_token":"a"}"""))

            assertThrows(IOException::class.java) { runBlocking { repository.signUp("me@example.com", "pw") } }

            assertNull(currentSession())
        }

    @Test
    fun aMalformedAnswerIsAnIoProblem() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("<html>Wi-Fi login</html>"))

            assertThrows(IOException::class.java) { runBlocking { repository.signIn("me@example.com", "pw") } }

            assertNull(currentSession())
        }

    @Test
    fun redirectsAreNotFollowed() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(307).setHeader("Location", server.url("/elsewhere").toString()),
            )

            val error = runCatching { repository.signIn("me@example.com", "pw") }.exceptionOrNull()

            assertEquals(307, (error as ApiException).code)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun signOutClearsTheSessionBeforeTheServerIsCalled() =
        runTest {
            sessionStore.save(Session("user-token", "r", "me@example.com"))
            val sessionWhenLogoutArrived = arrayOfNulls<Session>(1)
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        sessionWhenLogoutArrived[0] = runBlocking { sessionStore.current() }
                        return MockResponse().setResponseCode(204)
                    }
                }

            repository.signOut()

            assertNull(currentSession())
            val request = server.takeRequest(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertNotNull("no logout request", request)
            assertEquals("POST", request?.method)
            assertEquals("/auth/v1/logout", request?.path)
            assertEquals(ANON_KEY, request?.getHeader("apikey"))
            assertEquals("Bearer user-token", request?.getHeader("Authorization"))
            assertNull(sessionWhenLogoutArrived[0])
        }

    @Test
    fun signOutStillClearsLocallyWhenTheServerFails() =
        runTest {
            sessionStore.save(Session("user-token", "r", null))
            server.shutdown()

            repository.signOut()

            assertNull(currentSession())
        }

    @Test
    fun signOutWithoutASessionSendsNothing() =
        runTest {
            repository.signOut()

            assertFalse(server.takeRequest(SHORT_WAIT_MS, TimeUnit.MILLISECONDS) != null)
        }

    @Test
    fun signOutWithoutAConnectionOnlyClearsLocally() =
        runTest {
            sessionStore.save(Session("user-token", "r", null))
            connectionStore.clear()

            repository.signOut()

            assertNull(currentSession())
            assertEquals(0, server.requestCount)
        }

    private companion object {
        const val ANON_KEY = "anon-key"
        const val TIMEOUT_SECONDS = 5L
        const val SHORT_WAIT_MS = 300L
    }
}
