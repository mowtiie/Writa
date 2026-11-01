package com.mowtiie.writa.core.network

import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.OkHttpClient

val apiJson: Json = Json { ignoreUnknownKeys = true }

/**
 * Both clients refuse redirects: one could replay the refresh token or the `apikey` to another host, or downgrade
 * from https to http.
 */
object HttpClients {
    /** For the setup probe, auth endpoints and token refresh. It never carries an `Authenticator`. */
    fun plain(): OkHttpClient =
        OkHttpClient
            .Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    /** For every `/rest/v1/` call. Shares the connection pool of [plain]. */
    fun authed(
        plain: OkHttpClient,
        authenticator: Authenticator,
    ): OkHttpClient = plain.newBuilder().authenticator(authenticator).build()
}
