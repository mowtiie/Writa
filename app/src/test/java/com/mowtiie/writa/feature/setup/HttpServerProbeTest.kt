package com.mowtiie.writa.feature.setup

import com.mowtiie.writa.core.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException
import javax.net.ssl.SSLHandshakeException

class HttpServerProbeTest {
    private val server = MockWebServer()
    private lateinit var probe: HttpServerProbe

    private val baseUrl get() = server.url("/").toString().removeSuffix("/")

    @Before
    fun setUp() {
        server.start()
        probe = HttpServerProbe(HttpClients.plain(), Dispatchers.IO)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun check(
        url: String = baseUrl,
        key: String = "anon-key",
    ) = runBlocking { probe.check(url, key) }

    @Test
    fun aServerThatAnswersIsReachable() {
        server.enqueue(MockResponse().setBody("""{"external":{}}"""))

        assertEquals(ProbeResult.Reachable, check())
    }

    @Test
    fun theProbeAsksForTheAuthSettingsWithOnlyTheKey() {
        server.enqueue(MockResponse().setBody("{}"))

        check(key = "my-key")

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/auth/v1/settings", request.path)
        assertEquals("my-key", request.getHeader("apikey"))
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun aRejectedKeyIsToldApartFromAWrongServer() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Invalid API key"}"""))
        server.enqueue(MockResponse().setResponseCode(403))

        assertEquals(ProbeResult.KeyRejected(401), check())
        assertEquals(ProbeResult.KeyRejected(403), check())
    }

    @Test
    fun otherAnswersMeanItIsProbablyNotASupabaseServer() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("<html>Not found</html>"))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(204).setBody(""))

        assertEquals(ProbeResult.UnexpectedResponse(404), check())
        assertEquals(ProbeResult.UnexpectedResponse(500), check())
        assertEquals(ProbeResult.Reachable, check())
    }

    @Test
    fun aRedirectIsNotFollowedAndCountsAsAnUnexpectedAnswer() {
        server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", server.url("/elsewhere").toString()))

        assertEquals(ProbeResult.UnexpectedResponse(307), check())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun aServerThatIsDownIsNotReached() {
        val url = baseUrl
        server.shutdown()

        assertEquals(ProbeResult.NotReached, check(url))
    }

    @Test
    fun aUrlThatCannotBeParsedIsInvalid() {
        assertEquals(ProbeResult.InvalidUrl, check("https://exa mple.com"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aCertificateProblemIsToldApart() {
        val failing = failingClient(SSLHandshakeException("untrusted"))

        assertEquals(
            ProbeResult.SecureConnectionProblem,
            runBlocking {
                HttpServerProbe(failing, Dispatchers.IO).check(baseUrl, "k")
            },
        )
    }

    @Test
    fun anyOtherNetworkFailureIsNotReached() {
        val failing = failingClient(IOException("reset"))

        assertEquals(
            ProbeResult.NotReached,
            runBlocking { HttpServerProbe(failing, Dispatchers.IO).check(baseUrl, "k") },
        )
    }

    private fun failingClient(error: IOException): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { throw error }.build()
}
