package com.mowtiie.writa.feature.settings.backup

import com.mowtiie.writa.feature.notes.Note
import com.mowtiie.writa.feature.notes.Tags
import com.mowtiie.writa.feature.notes.remote.NoteInsert
import java.io.IOException

/** The file is not a backup: it is malformed, or has no notes array. */
class InvalidBackupException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/** The backup was made by a newer version of the app than this one understands. */
class UnsupportedBackupVersionException(
    val version: Int,
) : IOException("Backup version $version is newer than this app understands")

/** A note as the file holds it. No ids are exported, and `createdAt` is informational only. */
data class BackupNote(
    val title: String,
    val content: String,
    val createdAt: String?,
    val tags: List<String>,
    val pinned: Boolean,
    val archived: Boolean,
)

/** One entry of the `notes` array as read from the file; anything of the wrong type has already been dropped. */
data class RawBackupNote(
    val title: String?,
    val content: String?,
    val createdAt: String?,
    val pinned: Boolean?,
    val archived: Boolean?,
    val tags: List<String>?,
)

/** What was read from the file, in whatever order its keys came. A null [notes] means there was no array. */
data class RawBackup(
    val version: Int?,
    val notes: List<RawBackupNote>?,
)

/** The rules of the backup format, on already-read values so they run on the JVM. */
object BackupRules {
    const val VERSION = 1

    /**
     * The notes of a valid backup. Entries without a usable title are skipped.
     * @throws UnsupportedBackupVersionException if the file is from a newer format
     * @throws InvalidBackupException if there is no notes array
     */
    fun interpret(raw: RawBackup): List<BackupNote> {
        if (raw.version != null && raw.version > VERSION) throw UnsupportedBackupVersionException(raw.version)
        val notes = raw.notes ?: throw InvalidBackupException("Backup has no notes array")
        return notes.mapNotNull(::accept)
    }

    private fun accept(raw: RawBackupNote): BackupNote? {
        val title = raw.title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return BackupNote(
            title = title,
            content = raw.content.orEmpty(),
            createdAt = raw.createdAt,
            tags = Tags.normalize(raw.tags),
            pinned = raw.pinned ?: false,
            archived = raw.archived ?: false,
        )
    }

    /** Trashed notes are not exported; archived ones are. */
    fun selectForExport(notes: List<Note>): List<Note> = notes.filterNot { it.isTrashed }

    fun toBackupNote(note: Note): BackupNote =
        BackupNote(
            title = note.title,
            content = note.content.orEmpty(),
            createdAt = note.createdAt.takeIf { it.isNotEmpty() },
            tags = note.tags,
            pinned = note.pinned,
            archived = note.archived,
        )

    /** Import is a merge that always creates new notes, so the original creation time is not carried over. */
    fun toInsert(note: BackupNote): NoteInsert =
        NoteInsert(
            title = note.title,
            content = note.content,
            tags = note.tags,
            pinned = note.pinned,
            archived = note.archived,
        )
}
