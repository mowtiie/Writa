package com.mowtiie.writa.core.network

import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object NetworkErrors {
    /** The server could not be reached at all. `SocketTimeoutException` is an `InterruptedIOException`. */
    fun isConnectivityProblem(error: Throwable?): Boolean =
        error is UnknownHostException ||
            error is ConnectException ||
            error is NoRouteToHostException ||
            error is InterruptedIOException

    fun isSecureConnectionProblem(error: Throwable?): Boolean = error is SSLException
}
