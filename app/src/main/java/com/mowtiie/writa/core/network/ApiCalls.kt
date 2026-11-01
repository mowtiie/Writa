package com.mowtiie.writa.core.network

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resumeWithException

/**
 * Runs [request] and returns the body of a 2xx answer. Cancelling the coroutine cancels the call. Anything else
 * is an [ApiException]; connection problems surface as the original [IOException]. The body is read on
 * [ioDispatcher], so callers may be on any thread.
 */
suspend fun OkHttpClient.executeApi(
    request: Request,
    ioDispatcher: CoroutineDispatcher,
): String {
    val response = newCall(request).await()
    return withContext(ioDispatcher) {
        response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw ApiException.of(it.code, text)
            text
        }
    }
}

private suspend fun Call.await(): Response =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    continuation.resume(response) { _, closedResponse, _ -> closedResponse.close() }
                }
            },
        )
    }
