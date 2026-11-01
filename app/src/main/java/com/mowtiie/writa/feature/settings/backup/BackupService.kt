package com.mowtiie.writa.feature.settings.backup

import com.mowtiie.writa.core.di.IoDispatcher
import com.mowtiie.writa.feature.notes.NoteRepository
import com.mowtiie.writa.feature.notes.remote.NoteRemoteDataSource
import com.mowtiie.writa.feature.notes.remote.PartialImportException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Export and import of the notes as a plain-text backup file. Export reads the notes on this device; import
 * inserts new notes on the server (it merges, never deletes) and then refreshes, so it needs the network.
 */
@Singleton
class BackupService
    @Inject
    constructor(
        private val notes: NoteRepository,
        private val remote: NoteRemoteDataSource,
        private val codec: BackupCodec,
        private val clock: Clock,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        /** Writes every note that is not in the trash, archived ones included. */
        suspend fun export(out: OutputStream) {
            val all = notes.observeActive().first() + notes.observeArchived().first()
            val backup = BackupRules.selectForExport(all).map(BackupRules::toBackupNote)
            withContext(ioDispatcher) { codec.write(out, backup, clock.instant()) }
        }

        /** The notes of a backup, for the user to confirm. Throws [InvalidBackupException] or [UnsupportedBackupVersionException]. */
        suspend fun read(input: InputStream): List<BackupNote> = withContext(ioDispatcher) { codec.read(input) }

        /**
         * Inserts [backup] on the server in batches and refreshes, so the new notes show. Returns how many went in.
         * A failure after some batches throws [PartialImportException]; the notes that did go in are shown anyway.
         */
        suspend fun import(backup: List<BackupNote>): Int {
            if (backup.isEmpty()) return 0
            val imported =
                try {
                    remote.insertAll(backup.map(BackupRules::toInsert))
                } catch (e: PartialImportException) {
                    notes.refresh()
                    throw e
                }
            notes.refresh()
            return imported
        }

        fun suggestedFileName(): String = "writa_backup_${FILE_STAMP.format(clock.instant())}.json"

        private companion object {
            val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC)
        }
    }
