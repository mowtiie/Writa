package com.mowtiie.writa.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** An in-memory [NoteDao] that follows the SQL of the real one. [NoteDaoTest] (instrumented) checks the real one. */
class FakeNoteDao : NoteDao {
    private val table = MutableStateFlow<Map<Long, NoteEntity>>(emptyMap())
    val aliases = mutableMapOf<Long, Long>()
    val metaValues = mutableMapOf<String, String?>()

    val rows: List<NoteEntity> get() = table.value.values.toList()

    private fun observing(filter: (NoteEntity) -> Boolean) =
        table.map { map -> map.values.filter(filter).sortedBy { it.position } }

    override fun observeActive(): Flow<List<NoteEntity>> =
        observing { !it.pendingDelete && !it.archived && it.deletedAt == null }

    override fun observeArchived(): Flow<List<NoteEntity>> =
        observing { !it.pendingDelete && it.archived && it.deletedAt == null }

    override fun observeTrashed(): Flow<List<NoteEntity>> = observing { !it.pendingDelete && it.deletedAt != null }

    override suspend fun get(id: Long): NoteEntity? = table.value[id]

    override suspend fun everything(): List<NoteEntity> = rows

    override suspend fun pending(): List<NoteEntity> =
        rows.filter { !it.syncFailed && (it.dirtyMask != 0 || it.pendingDelete || it.id <= -2) }

    override fun observePendingCount(): Flow<Int> =
        table.map { map ->
            map.values.count { it.dirtyMask != 0 || it.pendingDelete || it.id <= -2 || it.syncFailed }
        }

    override suspend fun positionOf(id: Long): Long? = table.value[id]?.position

    override suspend fun minPosition(): Long? = rows.minOfOrNull { it.position }

    override suspend fun lowestUsedId(): Long? = (rows.map { it.id } + aliases.keys).minOrNull()

    override suspend fun insert(note: NoteEntity) {
        table.value = table.value + (note.id to note)
    }

    override suspend fun insertAll(notes: List<NoteEntity>) {
        table.value = table.value + notes.associateBy { it.id }
    }

    override suspend fun setPosition(
        id: Long,
        position: Long,
    ) {
        table.value[id]?.let { insert(it.copy(position = position)) }
    }

    override suspend fun setFailed(
        id: Long,
        failed: Boolean,
    ) {
        table.value[id]?.let { insert(it.copy(syncFailed = failed)) }
    }

    override suspend fun delete(id: Long) {
        table.value = table.value - id
    }

    override suspend fun deleteAll() {
        table.value = emptyMap()
    }

    private val aliasTable = MutableStateFlow(0)

    override fun observeAliases(): Flow<List<IdAlias>> =
        aliasTable.map { aliases.map { (old, new) -> IdAlias(old, new) } }

    override suspend fun aliasOf(id: Long): Long? = aliases[id]

    override suspend fun putAlias(alias: IdAlias) {
        aliases[alias.oldId] = alias.newId
        aliasTable.value++
    }

    override suspend fun deleteAliases() {
        aliases.clear()
        aliasTable.value++
    }

    override suspend fun meta(key: String): String? = metaValues[key]

    override suspend fun putMeta(meta: CacheMeta) {
        metaValues[meta.key] = meta.value
    }

    override suspend fun deleteMeta() = metaValues.clear()
}

class PassThroughTransactionRunner : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}
