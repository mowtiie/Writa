package com.mowtiie.writa.feature.notes

import com.mowtiie.writa.core.network.ApiException
import com.mowtiie.writa.core.network.ConnectionNotConfiguredException
import com.mowtiie.writa.core.network.NotSignedInException
import com.mowtiie.writa.core.network.SchemaOutdatedException
import com.mowtiie.writa.feature.notes.cache.CacheUnavailableException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.GeneralSecurityException
import javax.net.ssl.SSLHandshakeException

class RefreshFailureTest {
    @Test
    fun anOutdatedSchemaHasItsOwnFailure() {
        assertEquals(RefreshFailure.SchemaOutdated, RefreshFailure.of(SchemaOutdatedException(listOf("tags"))))
    }

    @Test
    fun noSignInOrConnectionIsUnavailable() {
        assertEquals(RefreshFailure.Unavailable, RefreshFailure.of(ConnectionNotConfiguredException()))
        assertEquals(RefreshFailure.Unavailable, RefreshFailure.of(NotSignedInException()))
        assertEquals(RefreshFailure.Unavailable, RefreshFailure.of(CacheUnavailableException()))
    }

    @Test
    fun unreachableServersAreOffline() {
        assertEquals(RefreshFailure.Offline, RefreshFailure.of(UnknownHostException("x")))
        assertEquals(RefreshFailure.Offline, RefreshFailure.of(ConnectException("x")))
        assertEquals(RefreshFailure.Offline, RefreshFailure.of(SocketTimeoutException("x")))
    }

    @Test
    fun tlsProblemsAreTheirOwnFailure() {
        assertEquals(RefreshFailure.SecureConnection, RefreshFailure.of(SSLHandshakeException("bad cert")))
    }

    @Test
    fun serverAnswersAreServerFailures() {
        assertEquals(RefreshFailure.Server, RefreshFailure.of(ApiException.of(500, "")))
        assertEquals(RefreshFailure.Server, RefreshFailure.of(ApiException.of(403, """{"message":"denied"}""")))
    }

    @Test
    fun anythingElseIsOther() {
        assertEquals(RefreshFailure.Other, RefreshFailure.of(IOException("unreadable")))
        assertEquals(RefreshFailure.Other, RefreshFailure.of(GeneralSecurityException("no key")))
        assertEquals(RefreshFailure.Other, RefreshFailure.of(IllegalStateException()))
    }
}
