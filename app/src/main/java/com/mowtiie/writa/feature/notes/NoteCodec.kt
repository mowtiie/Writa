package com.mowtiie.writa.feature.notes

import com.mowtiie.writa.core.database.NoteEntity
import com.mowtiie.writa.core.security.NoteCrypto
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import javax.inject.Inject

/** Turns notes into encrypted rows and back. The text never reaches the database in the clear. */
class NoteCodec
    @Inject
    constructor(
        private val crypto: NoteCrypto,
        private val json: Json,
    ) {
        /** A row in sync with the server; callers `copy` the sync fields they need. Throws if encryption fails. */
        @Throws(GeneralSecurityException::class)
        fun encode(
            note: Note,
            position: Long,
        ): NoteEntity {
            val payload = json.encodeToString(NotePayload(note.title, note.content, note.tags))
            return NoteEntity(
                id = note.id,
                position = position,
                createdAt = note.createdAt,
                updatedAt = note.updatedAt,
                deletedAt = note.deletedAt,
                pinned = note.pinned,
                archived = note.archived,
                payload = crypto.encrypt(note.id, payload),
            )
        }

        /** The note, or null if its text cannot be read any more (the row is then skipped). */
        fun decode(entity: NoteEntity): Note? {
            val plain = crypto.decrypt(entity.id, entity.payload) ?: return null
            val payload =
                try {
                    json.decodeFromString<NotePayload>(plain)
                } catch (_: SerializationException) {
                    return null
                }
            return Note(
                id = entity.id,
                title = payload.title,
                content = payload.content,
                tags = payload.tags,
                pinned = entity.pinned,
                archived = entity.archived,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                deletedAt = entity.deletedAt,
                syncState = syncStateOf(entity),
            )
        }

        private fun syncStateOf(entity: NoteEntity): SyncState =
            when {
                entity.syncFailed -> SyncState.Failed
                entity.hasLocalChanges -> SyncState.Pending
                else -> SyncState.Synced
            }

        @Serializable
        private class NotePayload(
            val title: String,
            val content: String?,
            val tags: List<String>,
        )
    }
