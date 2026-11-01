package com.mowtiie.writa.core.network

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

/**
 * An unsuccessful HTTP response. Only the status code, a short JSON message and the short error code are kept:
 * PostgREST `details` can echo row contents, so the raw body never reaches an exception.
 *
 * [serverMessage] is the bare text from the server (PostgREST `message`, GoTrue `msg` or `error_description`),
 * cut to [MAX_MESSAGE_LENGTH] characters, or null when the body had none. [errorCode] is the PostgREST/Postgres
 * `code` string such as `42703`, or null.
 */
class ApiException private constructor(
    val code: Int,
    val serverMessage: String?,
    val errorCode: String?,
) : IOException(serverMessage?.let { "HTTP $code: $it" } ?: "HTTP $code") {
    companion object {
        const val MAX_MESSAGE_LENGTH = 200
        private val MESSAGE_KEYS = listOf("message", "msg", "error_description")

        fun of(
            code: Int,
            body: String,
        ): ApiException {
            val fields = parseObject(body)
            return ApiException(code, fields?.let(::messageOf)?.let(::shorten), fields?.let { text(it, "code") })
        }

        private fun messageOf(fields: JsonObject): String? = MESSAGE_KEYS.firstNotNullOfOrNull { text(fields, it) }

        private fun text(
            fields: JsonObject,
            key: String,
        ): String? =
            fields[key]
                ?.jsonPrimitive
                ?.takeIf { it.isString }
                ?.content
                ?.takeIf { it.isNotEmpty() }

        private fun shorten(message: String): String =
            if (message.length > MAX_MESSAGE_LENGTH) "${message.take(MAX_MESSAGE_LENGTH)}..." else message

        private fun parseObject(body: String): JsonObject? =
            try {
                Json.parseToJsonElement(body).jsonObject
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
    }
}
