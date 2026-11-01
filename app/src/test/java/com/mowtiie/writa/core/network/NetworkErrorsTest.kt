package com.mowtiie.writa.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class NetworkErrorsTest {
    @Test
    fun unreachableServersAreConnectivityProblems() {
        assertTrue(NetworkErrors.isConnectivityProblem(UnknownHostException("example.invalid")))
        assertTrue(NetworkErrors.isConnectivityProblem(ConnectException("refused")))
        assertTrue(NetworkErrors.isConnectivityProblem(NoRouteToHostException("no route")))
        assertTrue(NetworkErrors.isConnectivityProblem(SocketTimeoutException("timeout")))
    }

    @Test
    fun otherFailuresAreNotConnectivityProblems() {
        assertFalse(NetworkErrors.isConnectivityProblem(IOException("something else")))
        assertFalse(NetworkErrors.isConnectivityProblem(ApiException.of(500, "")))
        assertFalse(NetworkErrors.isConnectivityProblem(SSLHandshakeException("bad cert")))
        assertFalse(NetworkErrors.isConnectivityProblem(null))
    }

    @Test
    fun tlsFailuresAreSecureConnectionProblems() {
        assertTrue(NetworkErrors.isSecureConnectionProblem(SSLHandshakeException("bad cert")))
        assertFalse(NetworkErrors.isSecureConnectionProblem(UnknownHostException("x")))
        assertFalse(NetworkErrors.isSecureConnectionProblem(null))
    }
}
