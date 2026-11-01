package com.mowtiie.writa.core.network

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ApiCallsTest {
    private val server = MockWebServer()
    private val client = HttpClients.plain()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    private fun get() = Request.Builder().url(server.url("/x")).build()

    @Test
    fun returnsTheBodyOfASuccessfulAnswer() =
        runBlocking {
            server.enqueue(MockResponse().setBody("hello"))

            assertEquals("hello", client.executeApi(get(), Dispatchers.IO))
        }

    @Test
    fun anEmptySuccessBodyIsAnEmptyString() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(204))

            assertEquals("", client.executeApi(get(), Dispatchers.IO))
        }

    @Test
    fun otherStatusCodesAreApiExceptions() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(422).setBody("""{"msg":"nope"}"""))

            val error = runCatching { client.executeApi(get(), Dispatchers.IO) }.exceptionOrNull() as ApiException

            assertEquals(422, error.code)
            assertEquals("nope", error.serverMessage)
        }

    @Test
    fun connectionFailuresKeepTheirType() =
        runBlocking {
            server.shutdown()

            val error = runCatching { client.executeApi(get(), Dispatchers.IO) }.exceptionOrNull()

            assertTrue(NetworkErrors.isConnectivityProblem(error))
        }

    @Test
    fun cancellingTheCoroutineCancelsTheCall() =
        runBlocking {
            server.enqueue(MockResponse().setHeadersDelay(HANG_SECONDS, TimeUnit.SECONDS))
            val call = async(Dispatchers.IO, CoroutineStart.UNDISPATCHED) { client.executeApi(get(), Dispatchers.IO) }
            assertNotNull(server.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS))

            withTimeout(WAIT_SECONDS * 1000) { call.cancelAndJoin() }

            assertTrue(call.isCancelled)
        }

    private companion object {
        const val HANG_SECONDS = 2L
        const val WAIT_SECONDS = 5L
    }
}
