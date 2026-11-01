package com.mowtiie.writa.feature.notes.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.IOException

/** A row of the server's `notes` table. Every column the server may send as null is nullable here. */
@Serializable
data class RemoteNote(
    val id: Long,
    val title: String,
    val content: String? = null,
    val tags: List<String>? = null,
    val pinned: Boolean? = null,
    val archived: Boolean? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("client_id") val clientId: String? = null,
)

/** A note to create. `user_id` is never sent: the server fills it from the token. */
data class NoteInsert(
    val title: String,
    val content: String?,
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val clientId: String? = null,
    val deletedAt: String? = null,
)

/** The columns to change; null means leave as it is. */
data class NotePatch(
    val title: String? = null,
    val content: String? = null,
    val tags: List<String>? = null,
    val pinned: Boolean? = null,
    val archived: Boolean? = null,
    val deletedAt: DeletedAtChange? = null,
)

sealed interface DeletedAtChange {
    data class MoveToTrash(
        val timestamp: String,
    ) : DeletedAtChange

    data object Restore : DeletedAtChange
}

/** Some of the notes were inserted before a batch failed. */
class PartialImportException(
    val imported: Int,
    val total: Int,
    cause: Throwable,
) : IOException(cause.message, cause)
