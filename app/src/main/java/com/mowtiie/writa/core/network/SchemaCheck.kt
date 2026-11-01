package com.mowtiie.writa.core.network

import kotlinx.serialization.json.JsonObject
import java.io.IOException

/** The user's database lacks part of the v1.0 `schema.sql`; they have to run it again. */
class SchemaOutdatedException(
    val missingColumns: List<String>,
) : IOException("Database schema is outdated")

object SchemaCheck {
    val REQUIRED_COLUMNS =
        listOf(
            "id",
            "title",
            "content",
            "tags",
            "pinned",
            "archived",
            "created_at",
            "updated_at",
            "deleted_at",
            "client_id",
        )

    /** Postgres undefined column/table, and PostgREST column/table not found in its schema cache. */
    private val SCHEMA_ERROR_CODES = setOf("42703", "42P01", "PGRST204", "PGRST205")

    /** The required columns that a `select=*` row does not have. */
    fun missingColumns(row: JsonObject): List<String> = REQUIRED_COLUMNS.filter { it !in row }

    fun isSchemaProblem(error: ApiException): Boolean = error.errorCode in SCHEMA_ERROR_CODES
}
