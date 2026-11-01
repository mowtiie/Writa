package com.mowtiie.writa.feature.notes.remote

import com.mowtiie.writa.core.datastore.ConnectionStore
import com.mowtiie.writa.core.di.IoDispatcher
import com.mowtiie.writa.core.network.ApiException
import com.mowtiie.writa.core.network.AuthedClient
import com.mowtiie.writa.core.network.ConnectionNotConfiguredException
import com.mowtiie.writa.core.network.NotSignedInException
import com.mowtiie.writa.core.network.SchemaCheck
import com.mowtiie.writa.core.network.SchemaOutdatedException
import com.mowtiie.writa.core.network.executeApi
import com.mowtiie.writa.core.security.SessionStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PostgREST calls for the `notes` table, always through the authed client. Failures are [ApiException],
 * [SchemaOutdatedException] (the user has to run `schema.sql`), [ConnectionNotConfiguredException],
 * [NotSignedInException], or another [IOException] (network, TLS, unreadable answer).
 */
@Singleton
class NoteRemoteDataSource
    @Inject
    constructor(
        private val connectionStore: ConnectionStore,
        private val sessionStore: SessionStore,
        @AuthedClient private val client: OkHttpClient,
        private val json: Json,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        /** Every note, trashed and archived included, one page at a time. */
        suspend fun fetchAll(): List<RemoteNote> {
            val all = mutableListOf<RemoteNote>()
            while (true) {
                val page = fetchPage(offset = all.size)
                all += page
                if (page.size < PAGE_SIZE) return all
            }
        }

        /** Newest first; the id tie-break keeps pages stable when notes share a timestamp. */
        suspend fun fetchPage(
            offset: Int,
            limit: Int = PAGE_SIZE,
        ): List<RemoteNote> {
            val request =
                notesRequest {
                    addQueryParameter("select", "*")
                    addQueryParameter("order", "created_at.desc,id.desc")
                    addQueryParameter("limit", limit.toString())
                    addQueryParameter("offset", offset.toString())
                }.get().build()
            return parseNotes(call(request))
        }

        suspend fun fetch(id: Long): RemoteNote? {
            val request = notesRequest { addQueryParameter("id", "eq.$id") }.get().build()
            return parseNotes(call(request)).firstOrNull()
        }

        /** The note a retried upload already created, found by the id the app made for it. */
        suspend fun findByClientId(clientId: String): RemoteNote? {
            val request = notesRequest { addQueryParameter("client_id", "eq.$clientId") }.get().build()
            return parseNotes(call(request)).firstOrNull()
        }

        /** Throws [ApiException] with code 409 when a note with the same `client_id` already exists. */
        suspend fun create(note: NoteInsert): RemoteNote {
            val body = note.toJson(withPinned = true, withArchived = true, withTags = true, forCreate = true)
            val request = notesRequest {}.post(body.asBody()).header("Prefer", REPRESENTATION).build()
            return parseNotes(call(request)).firstOrNull() ?: throw IOException("Create returned no row")
        }

        /**
         * Changes a note, and only if its `updated_at` still equals [ifUpdatedAt] when that is given, so the edit is
         * known to be based on the server's current text. Null when nothing matched (changed or deleted elsewhere).
         */
        suspend fun update(
            id: Long,
            patch: NotePatch,
            ifUpdatedAt: String? = null,
        ): RemoteNote? {
            val request =
                notesRequest {
                    addQueryParameter("id", "eq.$id")
                    if (!ifUpdatedAt.isNullOrEmpty()) addQueryParameter("updated_at", "eq.$ifUpdatedAt")
                }.patch(patch.toJson().asBody()).header("Prefer", REPRESENTATION).build()
            return parseNotes(call(request)).firstOrNull()
        }

        suspend fun delete(id: Long) {
            call(notesRequest { addQueryParameter("id", "eq.$id") }.delete().build())
        }

        /**
         * Inserts [notes] in batches of [IMPORT_BATCH_SIZE]; stops at the first failing batch. A failure after some
         * batches went in is a [PartialImportException]. Returns how many were inserted.
         */
        suspend fun insertAll(notes: List<NoteInsert>): Int {
            var imported = 0
            for (batch in notes.chunked(IMPORT_BATCH_SIZE)) {
                try {
                    call(notesRequest {}.post(batch.toJson().asBody()).header("Prefer", MINIMAL).build())
                } catch (e: IOException) {
                    throw if (imported > 0) PartialImportException(imported, notes.size, e) else e
                }
                imported += batch.size
            }
            return imported
        }

        private suspend fun call(request: Request): String =
            try {
                client.executeApi(request, ioDispatcher)
            } catch (e: ApiException) {
                throw if (SchemaCheck.isSchemaProblem(e)) SchemaOutdatedException(emptyList()) else e
            }

        private suspend fun notesRequest(configureUrl: HttpUrl.Builder.() -> Unit): Request.Builder {
            val connection = connectionStore.current() ?: throw ConnectionNotConfiguredException()
            val token = sessionStore.current()?.accessToken ?: throw NotSignedInException()
            val url =
                "${connection.baseUrl}$NOTES_PATH".toHttpUrlOrNull()
                    ?: throw ConnectionNotConfiguredException()
            return Request
                .Builder()
                .url(url.newBuilder().apply(configureUrl).build())
                .header("apikey", connection.anonKey)
                .header("Authorization", "Bearer $token")
        }

        private fun JsonElement.asBody() = toString().toRequestBody(JSON)

        private fun parseNotes(text: String): List<RemoteNote> {
            try {
                val rows = json.parseToJsonElement(text).jsonArray
                val missing =
                    rows
                        .firstOrNull()
                        ?.jsonObject
                        ?.let(SchemaCheck::missingColumns)
                        .orEmpty()
                if (missing.isNotEmpty()) throw SchemaOutdatedException(missing)
                return json.decodeFromJsonElement(ListSerializer(RemoteNote.serializer()), rows)
            } catch (e: SerializationException) {
                throw IOException("Unreadable notes response", e)
            } catch (e: IllegalArgumentException) {
                throw IOException("Unreadable notes response", e)
            }
        }

        private fun NoteInsert.toJson(
            withPinned: Boolean,
            withArchived: Boolean,
            withTags: Boolean,
            forCreate: Boolean = false,
        ): JsonObject =
            buildJsonObject {
                put("title", title)
                put("content", content)
                if (withTags) putJsonArray("tags") { tags.forEach { add(JsonPrimitive(it)) } }
                if (withPinned) put("pinned", pinned)
                if (withArchived) put("archived", archived)
                if (forCreate && clientId != null) put("client_id", clientId)
                if (forCreate && deletedAt != null) put("deleted_at", deletedAt)
            }

        /** One insert request needs every object to have the same keys, so optional columns are all in or all out. */
        private fun List<NoteInsert>.toJson(): JsonArray {
            val withPinned = any { it.pinned }
            val withArchived = any { it.archived }
            val withTags = any { it.tags.isNotEmpty() }
            return JsonArray(map { it.toJson(withPinned, withArchived, withTags) })
        }

        private fun NotePatch.toJson(): JsonObject =
            buildJsonObject {
                title?.let { put("title", it) }
                content?.let { put("content", it) }
                tags?.let { list -> putJsonArray("tags") { list.forEach { add(JsonPrimitive(it)) } } }
                pinned?.let { put("pinned", it) }
                archived?.let { put("archived", it) }
                when (val change = deletedAt) {
                    is DeletedAtChange.MoveToTrash -> put("deleted_at", change.timestamp)
                    DeletedAtChange.Restore -> put("deleted_at", JsonNull)
                    null -> Unit
                }
            }

        companion object {
            const val PAGE_SIZE = 200
            const val IMPORT_BATCH_SIZE = 200
            private const val NOTES_PATH = "/rest/v1/notes"
            private const val REPRESENTATION = "return=representation"
            private const val MINIMAL = "return=minimal"
            private val JSON = "application/json".toMediaType()
        }
    }
