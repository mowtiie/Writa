package com.mowtiie.writa.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaCheckTest {
    private fun row(vararg keys: String) =
        Json.parseToJsonElement(keys.joinToString(prefix = "{", postfix = "}") { "\"$it\":null" }).jsonObject

    @Test
    fun aCompleteRowHasNoMissingColumns() {
        assertTrue(SchemaCheck.missingColumns(row(*SchemaCheck.REQUIRED_COLUMNS.toTypedArray(), "extra")).isEmpty())
    }

    @Test
    fun missingColumnsAreListedInSchemaOrder() {
        val withoutTwo = SchemaCheck.REQUIRED_COLUMNS - setOf("client_id", "tags")

        assertEquals(listOf("tags", "client_id"), SchemaCheck.missingColumns(row(*withoutTwo.toTypedArray())))
    }

    @Test
    fun aNullValueStillCountsAsPresent() {
        assertFalse("content" in SchemaCheck.missingColumns(row(*SchemaCheck.REQUIRED_COLUMNS.toTypedArray())))
    }

    @Test
    fun onlySchemaErrorCodesAreSchemaProblems() {
        assertTrue(SchemaCheck.isSchemaProblem(ApiException.of(400, """{"code":"42703"}""")))
        assertTrue(SchemaCheck.isSchemaProblem(ApiException.of(404, """{"code":"PGRST205"}""")))
        assertFalse(SchemaCheck.isSchemaProblem(ApiException.of(409, """{"code":"23505"}""")))
        assertFalse(SchemaCheck.isSchemaProblem(ApiException.of(500, "")))
    }
}
