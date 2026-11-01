package com.mowtiie.writa.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One note the server sent, or one created here. Title, content and tags are encrypted together in [payload];
 * ids, timestamps, flags and sync state stay readable so they can be queried.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: Long,
    /** Position in the server's order (0 is the top); notes made offline sit above it, below 0. */
    val position: Long,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String?,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    val pinned: Boolean,
    val archived: Boolean,
    val payload: String,
    /** Which parts changed since the server's copy, as a mix of the [DirtyMask] values. 0 when in sync. */
    @ColumnInfo(name = "dirty_mask", defaultValue = "0") val dirtyMask: Int = 0,
    /** The user deleted the note for good; the delete has not been uploaded yet. */
    @ColumnInfo(name = "pending_delete", defaultValue = "0") val pendingDelete: Boolean = false,
    /** The server refused the upload; the note waits for the user. */
    @ColumnInfo(name = "sync_failed", defaultValue = "0") val syncFailed: Boolean = false,
    /** The server's `updated_at` that the text edit was made on top of, to spot conflicts. */
    @ColumnInfo(name = "base_updated_at") val baseUpdatedAt: String? = null,
    /** Made by the app for a note created offline, so a retried upload cannot create it twice. */
    @ColumnInfo(name = "client_id") val clientId: String? = null,
    /** Goes up on every local change, so the sync engine can tell whether the note changed while uploading. */
    @ColumnInfo(defaultValue = "0") val version: Long = 0,
) {
    val isLocalOnly: Boolean get() = id <= NoteIds.FIRST_LOCAL

    val hasLocalChanges: Boolean get() = dirtyMask != 0 || pendingDelete || syncFailed || isLocalOnly
}

/** Where a note went when its temporary id was replaced by the server's, for screens still holding the old id. */
@Entity(tableName = "id_alias")
data class IdAlias(
    @PrimaryKey @ColumnInfo(name = "old_id") val oldId: Long,
    @ColumnInfo(name = "new_id") val newId: Long,
)

@Entity(tableName = "meta")
data class CacheMeta(
    @PrimaryKey val key: String,
    val value: String?,
)

object DirtyMask {
    /** The title or content changed. These are the changes that can conflict with another device. */
    const val TEXT = 1
    const val TAGS = 1 shl 1
    const val PINNED = 1 shl 2
    const val ARCHIVED = 1 shl 3

    /** Moved to the trash, or restored from it. */
    const val TRASH = 1 shl 4
    const val ALL = TEXT or TAGS or PINNED or ARCHIVED or TRASH
}
