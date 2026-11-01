package com.mowtiie.writa.feature.notes.cache

import com.mowtiie.writa.core.database.CacheMeta
import com.mowtiie.writa.core.database.DirtyMask
import com.mowtiie.writa.core.database.IdAlias
import com.mowtiie.writa.core.database.NoteDao
import com.mowtiie.writa.core.database.NoteEntity
import com.mowtiie.writa.core.database.NoteIds
import com.mowtiie.writa.core.database.TransactionRunner
import com.mowtiie.writa.core.di.DefaultDispatcher
import com.mowtiie.writa.feature.notes.Note
import com.mowtiie.writa.feature.notes.NoteCodec
import com.mowtiie.writa.feature.notes.PendingChange
import com.mowtiie.writa.feature.notes.Tags
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.GeneralSecurityException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The notes kept on the device: the last copy the server sent plus the changes made offline that it does not have
 * yet. Room is the single source of truth; the observers below are what the UI reads.
 *
 * The cache belongs to one account on one server. If the signed-in owner differs from the stored one, the old
 * contents (unsent changes included) are wiped before anything is read or written.
 *
 * Writes throw [CacheUnavailableException] when nobody is signed in and [GeneralSecurityException] when the text
 * cannot be encrypted; nothing is written in that case. Reads of a signed-out cache are empty.
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class NoteCache
    @Inject
    constructor(
        private val dao: NoteDao,
        private val transactions: TransactionRunner,
        private val codec: NoteCodec,
        private val ownerSource: CacheOwnerSource,
        private val clock: Clock,
        private val clientIds: ClientIdGenerator,
        @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) {
        private val mutex = Mutex()

        /** The owner the contents were last checked against, so the check is not repeated for every call. */
        private var verifiedOwner: String? = null

        // ---------------------------- reading ----------------------------

        fun observeActive(): Flow<List<Note>> = observe(dao::observeActive)

        fun observeArchived(): Flow<List<Note>> = observe(dao::observeArchived)

        fun observeTrashed(): Flow<List<Note>> = observe(dao::observeTrashed)

        /** How many notes have unsent changes, or failed for good. */
        fun observePendingCount(): Flow<Int> = observeOwned(0) { dao.observePendingCount().distinctUntilChanged() }

        /**
         * For each note that was uploaded under a new id, the id it had before. A list keyed by these keeps a note
         * in place (and its scroll position) when the server gives it its real id.
         */
        fun observeStableKeys(): Flow<Map<Long, Long>> =
            observeOwned(emptyMap<Long, Long>()) {
                dao
                    .observeAliases()
                    .map { aliases ->
                        aliases.associate { it.newId to it.oldId }
                    }.distinctUntilChanged()
            }

        /** One note, including its sync state. Follows ids that changed when a note was uploaded. */
        suspend fun get(id: Long): Note? =
            lockedOrNull {
                val row = dao.get(resolve(id))
                if (row == null || row.pendingDelete) null else codec.decode(row)
            }

        /** The id a note has now, for screens that still hold the temporary one it had before it was uploaded. */
        suspend fun resolveId(id: Long): Long = lockedOrNull { resolve(id) } ?: id

        private fun observe(source: () -> Flow<List<NoteEntity>>): Flow<List<Note>> =
            observeOwned(emptyList()) {
                source()
                    .map { rows -> rows.mapNotNull(codec::decode) }
                    .distinctUntilChanged()
                    .flowOn(defaultDispatcher)
            }

        private fun <T> observeOwned(
            whenSignedOut: T,
            source: () -> Flow<T>,
        ): Flow<T> =
            ownerSource.owner.flatMapLatest { owner ->
                if (owner == null) {
                    flowOf(whenSignedOut)
                } else {
                    flow {
                        mutex.withLock { verify(owner) }
                        emitAll(source())
                    }
                }
            }

        // ---------------------------- what the server sent ----------------------------

        /**
         * Brings the cache up to date with [serverNotes], in server order. Notes with changes waiting to upload keep
         * their local version and only follow the order; notes only on this device stay; every other note that is
         * not in the list is dropped. Nothing is written if a note cannot be encrypted.
         */
        suspend fun mergeServerNotes(serverNotes: List<Note>) =
            locked {
                val existing = dao.everything().associateBy { it.id }
                val serverIds = serverNotes.mapTo(HashSet()) { it.id }
                val fresh = mutableListOf<NoteEntity>()
                val reordered = mutableListOf<Pair<Long, Long>>()
                serverNotes.forEachIndexed { index, note ->
                    val position = index.toLong()
                    if (existing[note.id]?.hasLocalChanges == true) {
                        reordered += note.id to position
                    } else {
                        fresh += codec.encode(note, position)
                    }
                }
                val gone = existing.values.filter { it.id !in serverIds && !it.hasLocalChanges }.map { it.id }

                transactions.run {
                    reordered.forEach { (id, position) -> dao.setPosition(id, position) }
                    gone.forEach { dao.delete(it) }
                    dao.insertAll(fresh)
                }
            }

        /** Deletes everything, including unsent changes and which account the cache belongs to. */
        suspend fun clear() =
            mutex.withLock {
                verifiedOwner = null
                transactions.run {
                    dao.deleteAll()
                    dao.deleteAliases()
                    dao.deleteMeta()
                }
            }

        // ---------------------------- editing (offline first) ----------------------------

        /** Creates a note that exists only here, with a temporary id and a `client_id` for its upload. */
        suspend fun createLocal(
            title: String,
            content: String?,
            tags: List<String>,
            pinned: Boolean,
        ): Note =
            locked {
                val minId = dao.minId()
                val id = if (minId == null || minId > NoteIds.NO_NOTE) NoteIds.FIRST_LOCAL else minId - 1
                val position = (dao.minPosition() ?: 0) - 1
                val note =
                    Note(
                        id = id,
                        title = title,
                        content = content,
                        tags = Tags.normalize(tags),
                        pinned = pinned,
                        archived = false,
                        createdAt = clock.instant().toString(),
                        updatedAt = null,
                        deletedAt = null,
                    )
                val row = codec.encode(note, position).copy(version = 1, clientId = clientIds.next())
                dao.insert(row)
                decoded(row)
            }

        /**
         * Stores [note] (matched by id) as the new local version and marks the [changed] parts for upload.
         * Null if there is no such note, or it is being deleted.
         */
        suspend fun saveLocal(
            note: Note,
            changed: Int,
        ): Note? =
            locked {
                val old = dao.get(resolve(note.id))
                if (old == null || old.pendingDelete) return@locked null

                val edited = note.copy(id = old.id, createdAt = old.createdAt, updatedAt = old.updatedAt)
                val row =
                    codec.encode(edited, old.position).copy(
                        dirtyMask = old.dirtyMask or changed,
                        baseUpdatedAt = baseForEdit(old),
                        clientId = old.clientId,
                        version = old.version + 1,
                    )
                dao.insert(row)
                decoded(row)
            }

        /** The first edit of a synced note remembers which server version it was made on, to spot conflicts. */
        private fun baseForEdit(old: NoteEntity): String? =
            when {
                old.isLocalOnly -> null
                old.dirtyMask == 0 && !old.syncFailed -> old.updatedAt
                else -> old.baseUpdatedAt
            }

        /** Deletes a note for good: right away if the server never saw it, otherwise when the delete is uploaded. */
        suspend fun deleteLocal(id: Long): Boolean =
            locked {
                val row = dao.get(resolve(id)) ?: return@locked false
                if (row.isLocalOnly) {
                    dao.delete(row.id)
                } else {
                    dao.insert(row.copy(pendingDelete = true, syncFailed = false, version = row.version + 1))
                }
                true
            }

        // ---------------------------- uploading ----------------------------

        /** Notes with changes to upload: those made offline first, oldest first, then changes to existing notes. */
        suspend fun pending(): List<PendingChange> =
            lockedOrNull {
                dao
                    .pending()
                    .mapNotNull { row ->
                        codec.decode(row)?.let {
                            PendingChange(
                                it,
                                row.dirtyMask,
                                row.baseUpdatedAt,
                                row.clientId,
                                row.pendingDelete,
                                row.version,
                            )
                        }
                    }.sortedWith(
                        compareBy({ !it.note.isLocalOnly }, { if (it.note.isLocalOnly) -it.note.id else it.note.id }),
                    )
            } ?: emptyList()

        /**
         * The server accepted the note. [server] replaces the local copy, unless the note changed again since the
         * [version] that was uploaded; then the newer local text is kept and uploaded next time. The id may change
         * (a new note gets its real one), and the old one is remembered as an alias.
         */
        suspend fun syncSucceeded(
            id: Long,
            version: Long,
            server: Note,
        ) = locked {
            val old = dao.get(id)
            if (old == null) {
                keepDeletingCreatedCopy(id, server)
                return@locked
            }
            val row =
                if (old.version == version) {
                    codec.encode(server, old.position)
                } else {
                    changedDuringUpload(old, server) ?: return@locked
                }
            transactions.run {
                if (server.id != id) {
                    dao.delete(id)
                    dao.putAlias(IdAlias(oldId = id, newId = server.id))
                }
                dao.insert(row)
            }
        }

        /** The user deleted the note while it was uploading. If the upload created it, the new copy must go too. */
        private suspend fun keepDeletingCreatedCopy(
            id: Long,
            server: Note,
        ) {
            if (server.id != id) dao.insert(codec.encode(server, 0).copy(pendingDelete = true, version = 1))
        }

        /** Changed again while uploading: keep the newer local version, to be uploaded on top of the server's. */
        private fun changedDuringUpload(
            old: NoteEntity,
            server: Note,
        ): NoteEntity? {
            val local = codec.decode(old) ?: return null
            val merged = local.copy(id = server.id, createdAt = server.createdAt, updatedAt = server.updatedAt)
            return codec.encode(merged, old.position).copy(
                dirtyMask = if (old.isLocalOnly) DirtyMask.ALL else old.dirtyMask,
                baseUpdatedAt = server.updatedAt,
                pendingDelete = old.pendingDelete,
                version = old.version,
            )
        }

        /** The upload cannot work (the server refused it). The note stays, marked as failed, until the user acts. */
        suspend fun syncFailed(id: Long) = locked { dao.setFailed(resolve(id), true) }

        /** Lets a failed note be tried again. */
        suspend fun retryFailed(id: Long) = locked { dao.setFailed(resolve(id), false) }

        /** Forgets a note's local state and the note itself (it is gone on the server, or the user discarded it). */
        suspend fun syncDropped(id: Long) = locked { dao.delete(resolve(id)) }

        /** Throws away the local changes of a note and takes the server's version instead. */
        suspend fun replaceWithServer(
            id: Long,
            server: Note,
        ) = locked {
            val resolved = resolve(id)
            val row = codec.encode(server, dao.positionOf(resolved) ?: 0)
            transactions.run {
                dao.delete(resolved)
                dao.insert(row)
            }
        }

        // ---------------------------- internals ----------------------------

        private fun decoded(row: NoteEntity): Note =
            checkNotNull(codec.decode(row)) { "A note just encoded is unreadable" }

        /** Follows the id a note had before the server gave it its real one. */
        private suspend fun resolve(id: Long): Long {
            var current = id
            repeat(MAX_ALIAS_HOPS) { current = dao.aliasOf(current) ?: return current }
            return current
        }

        private suspend fun <T> locked(block: suspend () -> T): T =
            mutex.withLock {
                verify(ownerSource.owner.first() ?: throw CacheUnavailableException())
                block()
            }

        private suspend fun <T : Any> lockedOrNull(block: suspend () -> T?): T? =
            mutex.withLock {
                val owner = ownerSource.owner.first() ?: return@withLock null
                verify(owner)
                block()
            }

        private suspend fun verify(owner: String) {
            if (owner == verifiedOwner) return
            if (dao.meta(OWNER_KEY) != owner) {
                transactions.run {
                    dao.deleteAll()
                    dao.deleteAliases()
                    dao.putMeta(CacheMeta(OWNER_KEY, owner))
                }
            }
            verifiedOwner = owner
        }

        private companion object {
            const val OWNER_KEY = "owner"
            const val MAX_ALIAS_HOPS = 5
        }
    }
