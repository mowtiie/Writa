package com.mowtiie.writa.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiExceptionTest {
    @Test
    fun keepsStatusCodeAndShortMessage() {
        val error = ApiException.of(400, """{"message":"new row violates row-level security policy"}""")

        assertEquals(400, error.code)
        assertEquals("HTTP 400: new row violates row-level security policy", error.message)
    }

    @Test
    fun dropsDetailsThatCouldEchoRowContents() {
        val body =
            """{"message":"null value in column \"title\"","details":"Failing row contains (my private note text)"}"""

        val message = ApiException.of(400, body).message.orEmpty()

        assertTrue(message.contains("null value in column"))
        assertFalse(message.contains("my private note text"))
        assertFalse(message.contains("Failing row"))
    }

    @Test
    fun fallsBackToStatusCodeForNonJsonBodies() {
        assertEquals("HTTP 502", ApiException.of(502, "<html>Bad gateway</html>").message)
        assertEquals("HTTP 500", ApiException.of(500, "").message)
    }

    @Test
    fun fallsBackToStatusCodeWhenThereIsNoUsableMessageField() {
        assertEquals("HTTP 401", ApiException.of(401, """{"error":"unauthorized"}""").message)
        assertEquals("HTTP 401", ApiException.of(401, """{"message":""}""").message)
        assertEquals("HTTP 401", ApiException.of(401, """{"message":null}""").message)
        assertEquals("HTTP 401", ApiException.of(401, """{"message":42}""").message)
        assertEquals("HTTP 401", ApiException.of(401, """["message"]""").message)
    }

    @Test
    fun readsTheMessageShapesOfBothServices() {
        assertEquals(
            "User already registered",
            ApiException.of(422, """{"msg":"User already registered"}""").serverMessage,
        )
        val login = """{"error":"invalid_grant","error_description":"Invalid login credentials"}"""
        assertEquals("Invalid login credentials", ApiException.of(400, login).serverMessage)
        assertEquals("first", ApiException.of(400, """{"message":"first","msg":"second"}""").serverMessage)
        assertEquals("second", ApiException.of(400, """{"message":"","msg":"second"}""").serverMessage)
    }

    @Test
    fun serverMessageIsNullWhenThereIsNone() {
        assertNull(ApiException.of(502, "<html>Bad gateway</html>").serverMessage)
        assertNull(ApiException.of(401, """{"error":"unauthorized"}""").serverMessage)
    }

    @Test
    fun truncatesVeryLongMessages() {
        val body = """{"message":"${"x".repeat(500)}"}"""

        val message = ApiException.of(400, body).message.orEmpty()

        assertEquals("HTTP 400: ".length + ApiException.MAX_MESSAGE_LENGTH + "...".length, message.length)
        assertTrue(message.endsWith("..."))
    }
}
