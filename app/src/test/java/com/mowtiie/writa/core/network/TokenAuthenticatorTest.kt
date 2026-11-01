package com.mowtiie.writa.core.network

import com.mowtiie.writa.core.datastore.ConnectionStore
import com.mowtiie.writa.core.datastore.testPreferences
import com.mowtiie.writa.core.security.FakeSecureStorage
import com.mowtiie.writa.core.security.FakeSecureStorageFactory
import com.mowtiie.writa.core.security.Session
import com.mowtiie.writa.core.security.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TokenAuthenticatorTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val server = MockWebServer()
    private lateinit var connectionStore: ConnectionStore
    private lateinit var sessionStore: SessionStore
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server.start()
        connectionStore =
            ConnectionStore(
                testPreferences(scope, File(folder.root, "connection.preferences_pb")),
            )
        sessionStore =
            SessionStore(FakeSecureStorageFactory(mutableListOf(FakeSecureStorage())), Dispatchers.Unconfined)
        runBlocking {
            connectionStore.save(server.url("/").toString(), ANON_KEY)
            sessionStore.save(Session("old-access", "refresh-1", "me@example.com"))
        }

        val plain = HttpClients.plain()
        client = HttpClients.authed(plain, TokenAuthenticator(connectionStore, sessionStore, plain, apiJson))
    }

    @After
    fun tearDown() {
        server.shutdown()
        scope.cancel()
    }

    private fun callNotes(token: String = "old-access"): Int {
        val request =
            Request
                .Builder()
                .url(server.url("/rest/v1/notes"))
                .header("Authorization", "Bearer $token")
                .build()
        return client.newCall(request).execute().use { it.code }
    }

    private fun enqueueRefreshed() =
        server.enqueue(
            MockResponse()
                .setResponseCode(
                    200,
                ).setBody("""{"access_token":"new-access","refresh_token":"refresh-2"}"""),
        )

    private fun currentSession() = runBlocking { sessionStore.current() }

    @Test
    fun refreshesAnExpiredTokenAndRetriesTheRequest() {
        server.enqueue(MockResponse().setResponseCode(401))
        enqueueRefreshed()
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        assertEquals(200, callNotes())

        val first = server.takeRequest()
        val refresh = server.takeRequest()
        val retry = server.takeRequest()
        assertEquals("Bearer old-access", first.getHeader("Authorization"))
        assertEquals(REFRESH_PATH, refresh.path)
        assertEquals("POST", refresh.method)
        assertEquals(ANON_KEY, refresh.getHeader("apikey"))
        assertNull(refresh.getHeader("Authorization"))
        assertEquals("""{"refresh_token":"refresh-1"}""", refresh.body.readUtf8())
        assertEquals("Bearer new-access", retry.getHeader("Authorization"))
        assertEquals(Session("new-access", "refresh-2", "me@example.com"), currentSession())
    }

    @Test
    fun keepsTheOldRefreshTokenWhenTheResponseHasNoNewOne() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"access_token":"new-access","extra":1}"""))
        server.enqueue(MockResponse().setResponseCode(200))

        assertEquals(200, callNotes())

        assertEquals(Session("new-access", "refresh-1", "me@example.com"), currentSession())
    }

    @Test
    fun clearsTheSessionWhenTheRefreshTokenIsRejectedWith400() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant"}"""))

        assertEquals(401, callNotes())

        assertEquals(2, server.requestCount)
        assertNull(currentSession())
    }

    @Test
    fun clearsTheSessionWhenTheRefreshTokenIsRejectedWith401() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))

        assertEquals(401, callNotes())

        assertNull(currentSession())
    }

    @Test
    fun keepsTheSessionWhenACaptivePortalAnswersWithHtml() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html")
                .setBody("<html>Please log in to the Wi-Fi</html>"),
        )

        assertEquals(401, callNotes())

        assertEquals("old-access", currentSession()?.accessToken)
    }

    @Test
    fun keepsTheSessionWhenTheRefreshResponseHasNoAccessToken() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"unexpected":true}"""))

        callNotes()

        assertEquals(Session("old-access", "refresh-1", "me@example.com"), currentSession())
    }

    @Test
    fun keepsTheSessionWhenTheAccessTokenIsEmptyOrNull() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"access_token":""}"""))
        callNotes()

        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"access_token":null}"""))
        callNotes()

        assertEquals("old-access", currentSession()?.accessToken)
    }

    @Test
    fun keepsTheSessionOnServerErrors() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(503))

        assertEquals(401, callNotes())

        assertEquals("old-access", currentSession()?.accessToken)
    }

    @Test
    fun keepsTheSessionWhenTheConnectionDropsDuringRefresh() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AFTER_REQUEST))

        assertEquals(401, callNotes())

        assertEquals("old-access", currentSession()?.accessToken)
    }

    @Test
    fun doesNotFollowRedirectsAndKeepsTheSession() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(
            MockResponse().setResponseCode(307).setHeader("Location", server.url("/somewhere-else").toString()),
        )

        callNotes()

        assertEquals(2, server.requestCount)
        assertEquals("old-access", currentSession()?.accessToken)
    }

    @Test
    fun reusesATokenThatAnotherCallAlreadyRefreshed() {
        runBlocking { sessionStore.updateTokens("already-refreshed", "refresh-2") }
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        assertEquals(200, callNotes())

        server.takeRequest()
        assertEquals("Bearer already-refreshed", server.takeRequest().getHeader("Authorization"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun givesUpWithoutASession() {
        runBlocking { sessionStore.clear() }
        server.enqueue(MockResponse().setResponseCode(401))

        assertEquals(401, callNotes())

        assertEquals(1, server.requestCount)
    }

    @Test
    fun keepsTheSessionWhenTheConnectionIsNotConfigured() {
        runBlocking { connectionStore.clear() }
        server.enqueue(MockResponse().setResponseCode(401))

        assertEquals(401, callNotes())

        assertEquals(1, server.requestCount)
        assertEquals("old-access", currentSession()?.accessToken)
    }

    @Test
    fun givesUpWhenTheRetriedRequestIsRejectedAgain() {
        server.enqueue(MockResponse().setResponseCode(401))
        enqueueRefreshed()
        server.enqueue(MockResponse().setResponseCode(401))

        assertEquals(401, callNotes())

        assertEquals(3, server.requestCount)
        assertEquals("new-access", currentSession()?.accessToken)
    }

    @Test
    fun simultaneousFailuresRefreshOnlyOnce() {
        val refreshCalls =
            java.util.concurrent.atomic
                .AtomicInteger()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    when {
                        request.path == REFRESH_PATH -> {
                            refreshCalls.incrementAndGet()
                            Thread.sleep(REFRESH_DELAY_MS)
                            MockResponse().setBody("""{"access_token":"new-access","refresh_token":"refresh-2"}""")
                        }

                        request.getHeader("Authorization") == "Bearer new-access" -> {
                            MockResponse().setBody("[]")
                        }

                        else -> {
                            MockResponse().setResponseCode(401)
                        }
                    }
            }

        val calls = 4
        val pool = Executors.newFixedThreadPool(calls)
        val start = CountDownLatch(1)
        val results =
            (1..calls).map {
                pool.submit<Int> {
                    start.await()
                    callNotes()
                }
            }
        start.countDown()
        val codes = results.map { it.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        pool.shutdown()

        assertTrue(codes.all { it == 200 })
        assertEquals(1, refreshCalls.get())
    }

    private companion object {
        const val ANON_KEY = "anon-key"
        const val REFRESH_PATH = "/auth/v1/token?grant_type=refresh_token"
        const val REFRESH_DELAY_MS = 300L
        const val TIMEOUT_SECONDS = 20L
    }
}
