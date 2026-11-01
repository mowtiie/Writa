package com.mowtiie.writa.core.network

import com.mowtiie.writa.core.datastore.ConnectionStore
import com.mowtiie.writa.core.security.SessionStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import javax.inject.Inject

/**
 * Refreshes an expired access token. OkHttp requires this to block, so it is the one deliberately blocking class
 * in the networking layer. Only HTTP 400/401 from the refresh call clears the session; everything else (no
 * network, captive portals, 5xx, malformed bodies, no connection configured) keeps it.
 *
 * [plainClient] must be the client without an `Authenticator`.
 */
class TokenAuthenticator
    @Inject
    constructor(
        private val connectionStore: ConnectionStore,
        private val sessionStore: SessionStore,
        @PlainClient private val plainClient: OkHttpClient,
        private val json: Json,
    ) : Authenticator {
        override fun authenticate(
            route: Route?,
            response: Response,
        ): Request? {
            if (response.attemptCount() >= MAX_ATTEMPTS) return null

            synchronized(this) {
                val session = runBlocking { sessionStore.current() }
                val failedWith = response.request.bearerToken()
                if (session != null && session.accessToken != failedWith) {
                    return response.request.withBearer(session.accessToken)
                }

                val refreshToken = session?.refreshToken ?: return null
                return try {
                    val newAccess = refresh(refreshToken)
                    if (newAccess == null) {
                        runBlocking { sessionStore.clear() }
                        null
                    } else {
                        response.request.withBearer(newAccess)
                    }
                } catch (_: OfflineException) {
                    null
                }
            }
        }

        /** The new access token, or null only when the server definitively rejected the refresh token. */
        private fun refresh(refreshToken: String): String? {
            val connection = runBlocking { connectionStore.current() } ?: throw OfflineException()
            val url =
                "${connection.baseUrl}$REFRESH_PATH".toHttpUrlOrNull()
                    ?: throw OfflineException()
            val body = json.encodeToString(RefreshRequest(refreshToken)).toRequestBody(JSON)
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("apikey", connection.anonKey)
                    .post(body)
                    .build()

            try {
                plainClient.newCall(request).execute().use { result ->
                    if (result.code == HTTP_BAD_REQUEST || result.code == HTTP_UNAUTHORIZED) return null
                    if (!result.isSuccessful) throw OfflineException()

                    val responseBody = result.body ?: throw OfflineException()
                    val tokens = json.decodeFromString<RefreshResponse>(responseBody.string())
                    val access = tokens.accessToken?.takeIf { it.isNotEmpty() } ?: throw OfflineException()
                    runBlocking { sessionStore.updateTokens(access, tokens.refreshToken ?: refreshToken) }
                    return access
                }
            } catch (_: IOException) {
                throw OfflineException()
            } catch (_: IllegalArgumentException) {
                throw OfflineException()
            }
        }

        private fun Request.bearerToken(): String? = header("Authorization")?.removePrefix("Bearer ")

        private fun Request.withBearer(token: String): Request =
            newBuilder().header("Authorization", "Bearer $token").build()

        private fun Response.attemptCount(): Int = generateSequence(this) { it.priorResponse }.count()

        private class OfflineException : RuntimeException()

        @Serializable
        private class RefreshRequest(
            @SerialName("refresh_token") val refreshToken: String,
        )

        @Serializable
        private class RefreshResponse(
            @SerialName("access_token") val accessToken: String? = null,
            @SerialName("refresh_token") val refreshToken: String? = null,
        )

        private companion object {
            const val MAX_ATTEMPTS = 2
            const val REFRESH_PATH = "/auth/v1/token?grant_type=refresh_token"
            const val HTTP_BAD_REQUEST = 400
            const val HTTP_UNAUTHORIZED = 401
            val JSON = "application/json".toMediaType()
        }
    }
