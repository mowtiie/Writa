package com.mowtiie.writa.feature.notes

import com.mowtiie.writa.core.database.DirtyMask
import com.mowtiie.writa.feature.notes.cache.NoteCache
import com.mowtiie.writa.feature.notes.remote.NoteRemoteDataSource
import com.mowtiie.writa.feature.notes.remote.toNote
import com.mowtiie.writa.feature.notes.sync.SyncEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The notes the app shows. Room is the single source of truth: the observers read the cache, [refresh] and the
 * sync engine write into it, and no server response goes straight to the UI. Every edit is written locally first
 * (it throws if that fails, so a screen can stay open) and uploaded in the background.
 */
@Singleton
class NoteRepository
    @Inject
    constructor(
        private val cache: NoteCache,
        private val remote: NoteRemoteDataSource,
        private val syncEngine: SyncEngine,
        private val clock: Clock,
    ) {
        private val refreshLock = Mutex()
        private val _refreshState = MutableStateFlow<RefreshState>(RefreshState.Idle)

        val refreshState: StateFlow<RefreshState> = _refreshState.asStateFlow()

        // ---------------------------- reading ----------------------------

        fun observeActive(): Flow<List<Note>> = cache.observeActive()

        fun observeArchived(): Flow<List<Note>> = cache.observeArchived()

        fun observeTrashed(): Flow<List<Note>> = cache.observeTrashed()

        /** Notes with unsent changes, including the ones that failed for good. */
        fun observePendingCount(): Flow<Int> = cache.observePendingCount()

        /** Current id to the id a note first had, for list keys that must not change when an upload renames a note. */
        fun observeStableKeys(): Flow<Map<Long, Long>> = cache.observeStableKeys()

        /** Tags of the notes that exist (trash excluded), most used first, for suggestions in the editor. */
        fun observeKnownTags(): Flow<List<String>> =
            combine(cache.observeActive(), cache.observeArchived()) { active, archived ->
                Tags.usage((active + archived).map { it.tags }).keys.toList()
            }.distinctUntilChanged()

        suspend fun get(id: Long): Note? = cache.get(id)

        /** The id a note has now, for screens that still hold the temporary one it had before it was uploaded. */
        suspend fun resolveId(id: Long): Long = cache.resolveId(id)

        // ---------------------------- refreshing ----------------------------

        /**
         * Downloads every note and merges it into the cache. Unsent changes are uploaded first, and uploads wait
         * while the merge runs, so a note that was just uploaded is never overwritten by an older server copy.
         * The outcome is [refreshState]; a refresh that is already running is not started twice.
         */
        suspend fun refresh() {
            if (!refreshLock.tryLock()) return
            try {
                _refreshState.value = RefreshState.Loading
                syncEngine.sync()
                syncEngine.withPassesPaused { cache.mergeServerNotes(remote.fetchAll().map { it.toNote() }) }
                _refreshState.value = RefreshState.Idle
            } catch (e: CancellationException) {
                _refreshState.value = RefreshState.Idle
                throw e
            } catch (e: Exception) {
                _refreshState.value = RefreshState.Failed(RefreshFailure.of(e))
            } finally {
                refreshLock.unlock()
            }
        }

        // ---------------------------- editing ----------------------------

        suspend fun create(
            title: String,
            content: String?,
            tags: List<String>,
            pinned: Boolean,
        ): Note {
            val note = cache.createLocal(title, content, tags, pinned)
            syncEngine.trigger()
            return note
        }

        /**
         * Stores [edited], the whole wanted state of an existing note, and uploads what changed. Null if there is no
         * such note. Nothing is written when nothing changed.
         */
        suspend fun save(edited: Note): Note? {
            val stored = cache.get(edited.id) ?: return null
            val candidate = edited.copy(tags = Tags.normalize(edited.tags))
            val changed = changedParts(stored, candidate)
            if (changed == 0) return stored
            return cache.saveLocal(candidate, changed)?.also { syncEngine.trigger() }
        }

        suspend fun setPinned(
            id: Long,
            pinned: Boolean,
        ): Note? = change(id) { it.copy(pinned = pinned) }

        suspend fun setArchived(
            id: Long,
            archived: Boolean,
        ): Note? = change(id) { it.copy(archived = archived) }

        suspend fun moveToTrash(id: Long): Note? =
            change(id) { it.copy(deletedAt = it.deletedAt ?: clock.instant().toString()) }

        suspend fun restore(id: Long): Note? = change(id) { it.copy(deletedAt = null) }

        /** Deletes a note for good: at once if the server never saw it, otherwise when the delete is uploaded. */
        suspend fun deletePermanently(id: Long): Boolean {
            val deleted = cache.deleteLocal(id)
            if (deleted) syncEngine.trigger()
            return deleted
        }

        /** Lets a note whose upload the server refused be tried again. */
        suspend fun retryFailed(id: Long) {
            cache.retryFailed(id)
            syncEngine.trigger()
        }

        /** Forgets the unsent changes of a failed note; the server's copy comes back with the next refresh. */
        suspend fun discardFailed(id: Long) = cache.syncDropped(id)

        /** Deletes the local copy, including unsent changes. For sign out and Change server. */
        suspend fun clear() = cache.clear()

        private suspend fun change(
            id: Long,
            transform: (Note) -> Note,
        ): Note? = cache.get(id)?.let { save(transform(it)) }

        private fun changedParts(
            stored: Note,
            edited: Note,
        ): Int {
            var mask = 0
            if (stored.title != edited.title || stored.content != edited.content) mask = mask or DirtyMask.TEXT
            if (stored.tags != edited.tags) mask = mask or DirtyMask.TAGS
            if (stored.pinned != edited.pinned) mask = mask or DirtyMask.PINNED
            if (stored.archived != edited.archived) mask = mask or DirtyMask.ARCHIVED
            if (stored.isTrashed != edited.isTrashed) mask = mask or DirtyMask.TRASH
            return mask
        }
    }
