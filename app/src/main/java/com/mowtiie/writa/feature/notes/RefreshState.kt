package com.mowtiie.writa.feature.notes

import com.mowtiie.writa.core.network.ApiException
import com.mowtiie.writa.core.network.ConnectionNotConfiguredException
import com.mowtiie.writa.core.network.NetworkErrors
import com.mowtiie.writa.core.network.NotSignedInException
import com.mowtiie.writa.core.network.SchemaOutdatedException
import com.mowtiie.writa.feature.notes.cache.CacheUnavailableException

enum class RefreshFailure {
    /** The server could not be reached. */
    Offline,

    /** A TLS problem such as an untrusted or mismatched certificate. */
    SecureConnection,

    /** The user's database lacks part of `schema.sql`. */
    SchemaOutdated,

    /** The server answered with an error. */
    Server,

    /** Nobody is signed in on a configured server. */
    Unavailable,

    Other,
    ;

    companion object {
        fun of(error: Throwable): RefreshFailure =
            when {
                error is SchemaOutdatedException -> SchemaOutdated

                error is ConnectionNotConfiguredException ||
                    error is NotSignedInException ||
                    error is CacheUnavailableException -> Unavailable

                NetworkErrors.isSecureConnectionProblem(error) -> SecureConnection

                NetworkErrors.isConnectivityProblem(error) -> Offline

                error is ApiException -> Server

                else -> Other
            }
    }
}

sealed interface RefreshState {
    data object Idle : RefreshState

    data object Loading : RefreshState

    data class Failed(
        val reason: RefreshFailure,
    ) : RefreshState
}
