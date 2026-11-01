package com.mowtiie.writa.core.network

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class HttpClientsTest {
    private val authenticator =
        object : Authenticator {
            override fun authenticate(
                route: Route?,
                response: Response,
            ): Request? = null
        }

    @Test
    fun plainClientNeverFollowsRedirects() {
        val plain = HttpClients.plain()

        assertFalse(plain.followRedirects)
        assertFalse(plain.followSslRedirects)
    }

    @Test
    fun plainClientHasNoAuthenticator() {
        assertEquals(Authenticator.NONE, HttpClients.plain().authenticator)
    }

    @Test
    fun authedClientNeverFollowsRedirectsEither() {
        val authed = HttpClients.authed(HttpClients.plain(), authenticator)

        assertFalse(authed.followRedirects)
        assertFalse(authed.followSslRedirects)
    }

    @Test
    fun authedClientCarriesTheAuthenticatorAndPlainStaysUntouched() {
        val plain = HttpClients.plain()

        val authed = HttpClients.authed(plain, authenticator)

        assertSame(authenticator, authed.authenticator)
        assertEquals(Authenticator.NONE, plain.authenticator)
    }

    @Test
    fun bothClientsShareOneConnectionPool() {
        val plain = HttpClients.plain()

        assertSame(plain.connectionPool, HttpClients.authed(plain, authenticator).connectionPool)
    }
}
