package com.mowtiie.writa.feature.notes

/** A note with changes the server does not have yet, as handed to the sync engine. */
data class PendingChange(
    /** The note as it is on this device now. A temporary id (at most -2) means the server has not seen it yet. */
    val note: Note,
    /** Which parts changed since the server's copy, as a mix of the `DirtyMask` values. */
    val mask: Int,
    /** The server's `updated_at` that the text edit was made on top of; null if unknown. */
    val baseUpdatedAt: String?,
    /** Made by the app when the note was created offline, so a retried upload cannot create it twice. */
    val clientId: String?,
    /** The user deleted the note for good; only the server delete is left to do. */
    val pendingDelete: Boolean,
    /** Goes up on every local change, so the engine can tell whether the note changed while it was uploading. */
    val version: Long,
)
