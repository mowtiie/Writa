package com.mowtiie.writa.feature.notes.sync

import com.mowtiie.writa.core.database.DirtyMask
import com.mowtiie.writa.core.di.ApplicationScope
import com.mowtiie.writa.core.network.ApiException
import com.mowtiie.writa.feature.notes.Note
import com.mowtiie.writa.feature.notes.PendingChange
import com.mowtiie.writa.feature.notes.cache.ClientIdGenerator
import com.mowtiie.writa.feature.notes.cache.NoteCache
import com.mowtiie.writa.feature.notes.remote.DeletedAtChange
import com.mowtiie.writa.feature.notes.remote.NoteInsert
import com.mowtiie.writa.feature.notes.remote.NotePatch
import com.mowtiie.writa.feature.notes.remote.NoteRemoteDataSource
import com.mowtiie.writa.feature.notes.remote.toNote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Turns a note title into the title of its conflict copy. */
fun interface ConflictTitle {
    fun of(title: String): String
}

/**
 * Uploads the changes made while offline. One pass takes every note with changes waiting (new notes first) and sends
 * them one at a time; the first network or server problem stops the pass and everything left stays queued for the
 * next one. A change the server refuses for good only marks that note as failed, so it cannot block the others.
 *
 * Conflicts: a text edit is sent only if the server's `updated_at` is still the one the edit was based on. If it is
 * not, the user's version is kept as a separate conflict copy and the original takes the server's version, so
 * neither side's text is lost. Changes that do not touch the text (pin, archive, trash, tags) simply win.
 *
 * One worker coroutine consumes [trigger] signals; [runPass] is the same work for a caller that schedules itself.
 */
@Singleton
class SyncEngine
    @Inject
    constructor(
        private val cache: NoteCache,
        private val remote: NoteRemoteDataSource,
        private val conflictTitle: ConflictTitle,
        private val clientIds: ClientIdGenerator,
        @ApplicationScope scope: CoroutineScope,
    ) {
        private enum class Outcome { Done, Transient, Refused }

        private val passLock = Mutex()
        private val triggers = Channel<Unit>(Channel.CONFLATED)
        private val waitersLock = Any()
        private val waiters = mutableListOf<CompletableDeferred<Unit>>()

        init {
            scope.launch { work() }
        }

        /** Asks for a pass. Requests made while one runs are folded into one more pass afterwards. */
        fun trigger() {
            triggers.trySend(Unit)
        }

        /** Asks for a pass and returns once everything that could be uploaded has been tried. */
        suspend fun sync() {
            val finished = CompletableDeferred<Unit>()
            synchronized(waitersLock) { waiters += finished }
            trigger()
            finished.await()
        }

        /** One pass over the queue. False if it stopped early because of a network or server problem. */
        suspend fun runPass(): Boolean =
            passLock.withLock {
                for (change in cache.pending()) {
                    if (push(change) == Outcome.Transient) return@withLock false
                }
                true
            }

        /**
         * Runs [block] while no pass can run. A refresh needs this: a pass that finishes between fetching the server's
         * notes and merging them would be overwritten by the older copy that was fetched first.
         */
        suspend fun <T> withPassesPaused(block: suspend () -> T): T = passLock.withLock { block() }

        private suspend fun work() {
            for (signal in triggers) {
                val covered = takeWaiters()
                val clean = passOrFalse()
                if (!clean) {
                    // Do not hammer a server that just failed: requests that arrived meanwhile are not tried again.
                    triggers.tryReceive()
                    covered += takeWaiters()
                }
                covered.forEach { it.complete(Unit) }
            }
        }

        private suspend fun passOrFalse(): Boolean =
            try {
                runPass()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Whatever went wrong, the changes are still queued and the next pass tries again.
                false
            }

        private fun takeWaiters(): MutableList<CompletableDeferred<Unit>> =
            synchronized(waitersLock) { waiters.toMutableList().also { waiters.clear() } }

        private suspend fun push(change: PendingChange): Outcome =
            when {
                change.pendingDelete -> pushDelete(change)
                change.note.isLocalOnly -> pushCreate(change)
                else -> pushUpdate(change)
            }

        // ---------------------------- the three kinds of upload ----------------------------

        private suspend fun pushDelete(change: PendingChange): Outcome {
            val id = change.note.id
            return try {
                remote.delete(id)
                cache.syncDropped(id)
                Outcome.Done
            } catch (e: IOException) {
                failed(id, e)
            }
        }

        private suspend fun pushCreate(change: PendingChange): Outcome {
            val id = change.note.id
            return try {
                cache.syncSucceeded(id, change.version, remote.create(insertOf(change.note, change.clientId)).toNote())
                Outcome.Done
            } catch (e: IOException) {
                if (change.clientId != null &&
                    e.isStatus(HTTP_CONFLICT)
                ) {
                    adoptEarlierUpload(change, e)
                } else {
                    failed(id, e)
                }
            }
        }

        /** An earlier upload got through but its answer was lost: the note it made is this one. */
        private suspend fun adoptEarlierUpload(
            change: PendingChange,
            conflict: IOException,
        ): Outcome {
            val id = change.note.id
            return try {
                val existing = remote.findByClientId(checkNotNull(change.clientId))
                if (existing == null) {
                    failed(id, conflict)
                } else {
                    cache.syncSucceeded(id, change.version, existing.toNote())
                    Outcome.Done
                }
            } catch (e: IOException) {
                failed(id, e)
            }
        }

        private suspend fun pushUpdate(change: PendingChange): Outcome {
            val id = change.note.id
            val textChanged = change.mask and DirtyMask.TEXT != 0
            val base = if (textChanged) change.baseUpdatedAt else null
            return try {
                val updated = remote.update(id, patchOf(change.note, change.mask), base)
                when {
                    updated != null -> {
                        cache.syncSucceeded(id, change.version, updated.toNote())
                        Outcome.Done
                    }

                    // Nothing matched: the note is gone, or (for a text edit) someone else changed its text since.
                    !textChanged -> {
                        cache.syncDropped(id) // deleted elsewhere; an edit of its flags is moot
                        Outcome.Done
                    }

                    else -> {
                        resolveTextConflict(change)
                    }
                }
            } catch (e: IOException) {
                failed(id, e)
            }
        }

        private suspend fun resolveTextConflict(change: PendingChange): Outcome {
            val id = change.note.id
            val current = remote.fetch(id)
            if (current == null) {
                // Deleted elsewhere but edited here: keep the edit as a new note rather than lose it.
                val recreated = remote.create(insertOf(change.note, clientIds.next()))
                cache.syncSucceeded(id, change.version, recreated.toNote())
            } else {
                // Changed elsewhere and here: the user's text becomes its own note, and this one takes the server's.
                val copy =
                    change.note.copy(
                        title = conflictTitle.of(change.note.title),
                        pinned = false,
                        archived = false,
                    )
                cache.addServerNote(remote.create(insertOf(copy, clientIds.next())).toNote())
                cache.replaceWithServer(id, current.toNote())
            }
            return Outcome.Done
        }

        // ---------------------------- request bodies ----------------------------

        private fun insertOf(
            note: Note,
            clientId: String?,
        ) = NoteInsert(
            title = note.title,
            content = note.content,
            tags = note.tags,
            pinned = note.pinned,
            archived = note.archived,
            clientId = clientId,
            deletedAt = note.deletedAt,
        )

        private fun patchOf(
            note: Note,
            mask: Int,
        ): NotePatch {
            fun changed(part: Int) = mask and part != 0
            return NotePatch(
                title = note.title.takeIf { changed(DirtyMask.TEXT) },
                content = note.content.orEmpty().takeIf { changed(DirtyMask.TEXT) },
                tags = note.tags.takeIf { changed(DirtyMask.TAGS) },
                pinned = note.pinned.takeIf { changed(DirtyMask.PINNED) },
                archived = note.archived.takeIf { changed(DirtyMask.ARCHIVED) },
                deletedAt =
                    if (changed(DirtyMask.TRASH)) {
                        note.deletedAt?.let(DeletedAtChange::MoveToTrash) ?: DeletedAtChange.Restore
                    } else {
                        null
                    },
            )
        }

        // ---------------------------- errors ----------------------------

        /**
         * Network problems and server trouble are tried again later; only the server's refusal of this particular
         * change marks the note as failed.
         */
        private suspend fun failed(
            id: Long,
            error: IOException,
        ): Outcome {
            if (error !is ApiException || error.code in TRANSIENT_STATUSES || error.code >= HTTP_SERVER_ERROR) {
                return Outcome.Transient
            }
            cache.syncFailed(id)
            return Outcome.Refused
        }

        private fun IOException.isStatus(status: Int) = this is ApiException && code == status

        private companion object {
            const val HTTP_CONFLICT = 409
            const val HTTP_SERVER_ERROR = 500
            val TRANSIENT_STATUSES = setOf(401, 408, 429)
        }
    }
