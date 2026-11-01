package com.mowtiie.writa.feature.notes

import com.mowtiie.writa.core.database.NoteIds
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

enum class SyncState {
    Synced,

    /** Exists only on this device, or has changes the server does not have yet. */
    Pending,

    /** The server refused the upload; the note waits for the user. */
    Failed,
}

data class Note(
    val id: Long,
    val title: String,
    val content: String?,
    val tags: List<String>,
    val pinned: Boolean,
    val archived: Boolean,
    val createdAt: String,
    val updatedAt: String?,
    val deletedAt: String?,
    val syncState: SyncState = SyncState.Synced,
) {
    /** Notes created offline get a temporary id until the server assigns one. */
    val isLocalOnly: Boolean get() = id <= NoteIds.FIRST_LOCAL

    val isTrashed: Boolean get() = deletedAt != null

    /** Changed after creation: `updatedAt` is later than `createdAt` by at least one second. */
    val isEdited: Boolean
        get() {
            val created = parseTimestamp(createdAt) ?: return false
            val updated = parseTimestamp(updatedAt) ?: return false
            return Duration.between(created, updated) >= EDITED_AFTER
        }

    private companion object {
        val EDITED_AFTER: Duration = Duration.ofSeconds(1)

        fun parseTimestamp(value: String?): OffsetDateTime? =
            try {
                value?.let(OffsetDateTime::parse)
            } catch (_: DateTimeParseException) {
                null
            }
    }
}
