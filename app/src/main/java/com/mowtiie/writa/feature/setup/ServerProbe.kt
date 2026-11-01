package com.mowtiie.writa.feature.setup

import com.mowtiie.writa.core.di.IoDispatcher
import com.mowtiie.writa.core.network.ApiException
import com.mowtiie.writa.core.network.NetworkErrors
import com.mowtiie.writa.core.network.PlainClient
import com.mowtiie.writa.core.network.executeApi
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject

sealed interface ProbeResult {
    data object Reachable : ProbeResult

    data object InvalidUrl : ProbeResult

    /** The server answered 401 or 403: it is there, but it does not accept this key. */
    data class KeyRejected(
        val code: Int,
    ) : ProbeResult

    data class UnexpectedResponse(
        val code: Int,
    ) : ProbeResult

    data object NotReached : ProbeResult

    data object SecureConnectionProblem : ProbeResult
}

/** Asks a server whether it is a Supabase project that accepts the key. */
interface ServerProbe {
    suspend fun check(
        baseUrl: String,
        key: String,
    ): ProbeResult
}

/** `GET /auth/v1/settings` through the plain client: it needs only the `apikey` and never follows redirects. */
class HttpServerProbe
    @Inject
    constructor(
        @PlainClient private val client: OkHttpClient,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ServerProbe {
        override suspend fun check(
            baseUrl: String,
            key: String,
        ): ProbeResult {
            val url = "$baseUrl$SETTINGS_PATH".toHttpUrlOrNull() ?: return ProbeResult.InvalidUrl
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("apikey", key)
                    .build()
            return try {
                client.executeApi(request, ioDispatcher)
                ProbeResult.Reachable
            } catch (e: ApiException) {
                answerOf(e)
            } catch (e: IOException) {
                failureOf(e)
            }
        }

        private fun answerOf(error: ApiException): ProbeResult =
            if (error.code in REJECTED_STATUSES) {
                ProbeResult.KeyRejected(error.code)
            } else {
                ProbeResult.UnexpectedResponse(error.code)
            }

        private fun failureOf(error: IOException): ProbeResult =
            if (NetworkErrors.isSecureConnectionProblem(error)) {
                ProbeResult.SecureConnectionProblem
            } else {
                ProbeResult.NotReached
            }

        private companion object {
            const val SETTINGS_PATH = "/auth/v1/settings"
            val REJECTED_STATUSES = setOf(401, 403)
        }
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class ServerProbeModule {
    @Binds
    abstract fun bindServerProbe(probe: HttpServerProbe): ServerProbe
}
