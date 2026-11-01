package com.mowtiie.writa.feature.auth

import com.mowtiie.writa.core.network.ApiException
import com.mowtiie.writa.core.network.ConnectionNotConfiguredException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class LoginMessageTest {
    @Test
    fun aRefusalCarriesTheServersOwnWords() {
        val error =
            ApiException.of(
                400,
                """{"error":"invalid_grant","error_description":"Invalid login credentials"}""",
            )

        assertEquals(LoginMessage.FromServer("Invalid login credentials"), LoginMessage.of(error))
    }

    @Test
    fun aRefusalWithoutAMessageIsGeneric() {
        assertEquals(LoginMessage.Generic, LoginMessage.of(ApiException.of(502, "<html>Bad gateway</html>")))
        assertEquals(LoginMessage.Generic, LoginMessage.of(ApiException.of(500, "")))
    }

    @Test
    fun noConnectionMeansTheServerIsNotSetUp() {
        assertEquals(LoginMessage.ConnectionNotSetUp, LoginMessage.of(ConnectionNotConfiguredException()))
    }

    @Test
    fun unreachableServersAreToldApartFromCertificateProblems() {
        assertEquals(LoginMessage.ServerUnreachable, LoginMessage.of(UnknownHostException("x")))
        assertEquals(LoginMessage.ServerUnreachable, LoginMessage.of(ConnectException("x")))
        assertEquals(LoginMessage.ServerUnreachable, LoginMessage.of(SocketTimeoutException("x")))
        assertEquals(LoginMessage.SecureConnectionProblem, LoginMessage.of(SSLHandshakeException("untrusted")))
    }

    @Test
    fun anythingElseIsGenericAndNeverShowsExceptionText() {
        val message = LoginMessage.of(IOException("Unable to resolve host \"secret.internal\""))

        assertEquals(LoginMessage.Generic, message)
    }
}
