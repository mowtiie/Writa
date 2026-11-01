package com.mowtiie.writa.feature.auth

import com.mowtiie.writa.core.datastore.Connection
import com.mowtiie.writa.core.datastore.ConnectionStore
import com.mowtiie.writa.core.di.ApplicationScope
import com.mowtiie.writa.core.di.IoDispatcher
import com.mowtiie.writa.core.network.ConnectionNotConfiguredException
import com.mowtiie.writa.core.network.PlainClient
import com.mowtiie.writa.core.network.executeApi
import com.mowtiie.writa.core.security.Session
import com.mowtiie.writa.core.security.SessionStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class SignUpResult {
    SignedIn,

    /** The server created the account but sent no session because the email has to be confirmed first. */
    ConfirmationRequired,
}

/**
 * Account calls against GoTrue (`/auth/v1/`). They need only the `apikey`, so they use the plain client.
 * Failures are [ApiException] (the server refused), [ConnectionNotConfiguredException], or another [IOException]
 * (network, TLS, unreadable answer); the caller maps them to user-facing text.
 */
@Singleton
class AuthRepository
    @Inject
    constructor(
        private val connectionStore: ConnectionStore,
        private val sessionStore: SessionStore,
        @PlainClient private val client: OkHttpClient,
        private val json: Json,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        @ApplicationScope private val applicationScope: CoroutineScope,
    ) {
        suspend fun signUp(
            email: String,
            password: String,
        ): SignUpResult {
            val tokens = requestSession(SIGN_UP_PATH, email, password)
            if (tokens.accessToken.isNullOrEmpty()) return SignUpResult.ConfirmationRequired
            saveSession(tokens)
            return SignUpResult.SignedIn
        }

        suspend fun signIn(
            email: String,
            password: String,
        ) {
            saveSession(requestSession(SIGN_IN_PATH, email, password))
        }

        /** Ends the session locally first, then asks the server to revoke it without making the caller wait. */
        suspend fun signOut() {
            val token = sessionStore.current()?.accessToken
            val connection = connectionStore.current()
            sessionStore.clear()
            if (token == null || connection == null) return
            applicationScope.launch(ioDispatcher) { revoke(connection, token) }
        }

        private suspend fun requestSession(
            path: String,
            email: String,
            password: String,
        ): AuthResponse {
            val connection = connectionStore.current() ?: throw ConnectionNotConfiguredException()
            val body = json.encodeToString(Credentials(email, password)).toRequestBody(JSON)
            val request = authRequest(connection, path).post(body).build()
            return parse(client.executeApi(request, ioDispatcher))
        }

        private fun parse(text: String): AuthResponse =
            try {
                json.decodeFromString(text)
            } catch (e: SerializationException) {
                throw IOException("Unreadable auth response", e)
            }

        private suspend fun saveSession(tokens: AuthResponse) {
            val access = tokens.accessToken?.takeIf { it.isNotEmpty() }
            val refresh = tokens.refreshToken?.takeIf { it.isNotEmpty() }
            if (access == null || refresh == null) throw IOException("Auth response had no session")
            sessionStore.save(Session(access, refresh, tokens.user?.email))
        }

        private fun revoke(
            connection: Connection,
            token: String,
        ) {
            val request =
                authRequest(connection, LOGOUT_PATH)
                    .header("Authorization", "Bearer $token")
                    .post("".toRequestBody(JSON))
                    .build()
            try {
                client.newCall(request).execute().close()
            } catch (_: IOException) {
                // Best effort: the local session is already gone.
            }
        }

        private fun authRequest(
            connection: Connection,
            path: String,
        ): Request.Builder {
            val url: HttpUrl =
                "${connection.baseUrl}$path".toHttpUrlOrNull() ?: throw ConnectionNotConfiguredException()
            return Request.Builder().url(url).header("apikey", connection.anonKey)
        }

        @Serializable
        private class Credentials(
            val email: String,
            val password: String,
        )

        @Serializable
        private class AuthResponse(
            @SerialName("access_token") val accessToken: String? = null,
            @SerialName("refresh_token") val refreshToken: String? = null,
            val user: User? = null,
        )

        @Serializable
        private class User(
            val email: String? = null,
        )

        private companion object {
            const val SIGN_UP_PATH = "/auth/v1/signup"
            const val SIGN_IN_PATH = "/auth/v1/token?grant_type=password"
            const val LOGOUT_PATH = "/auth/v1/logout"
            val JSON = "application/json".toMediaType()
        }
    }
