package com.mowtiie.writa.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query(
        "SELECT * FROM notes WHERE pending_delete = 0 AND archived = 0 AND deleted_at IS NULL ORDER BY position ASC",
    )
    fun observeActive(): Flow<List<NoteEntity>>

    @Query(
        "SELECT * FROM notes WHERE pending_delete = 0 AND archived = 1 AND deleted_at IS NULL ORDER BY position ASC",
    )
    fun observeArchived(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE pending_delete = 0 AND deleted_at IS NOT NULL ORDER BY position ASC")
    fun observeTrashed(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): NoteEntity?

    @Query("SELECT * FROM notes")
    suspend fun everything(): List<NoteEntity>

    /** Notes with something to upload. Failed ones wait for the user. */
    @Query("SELECT * FROM notes WHERE sync_failed = 0 AND (dirty_mask != 0 OR pending_delete = 1 OR id <= -2)")
    suspend fun pending(): List<NoteEntity>

    /** Notes with unsent changes, including the ones that failed for good. */
    @Query("SELECT COUNT(*) FROM notes WHERE dirty_mask != 0 OR pending_delete = 1 OR id <= -2 OR sync_failed = 1")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT position FROM notes WHERE id = :id")
    suspend fun positionOf(id: Long): Long?

    @Query("SELECT MIN(position) FROM notes")
    suspend fun minPosition(): Long?

    @Query("SELECT MIN(id) FROM (SELECT id FROM notes UNION ALL SELECT old_id AS id FROM id_alias)")
    suspend fun lowestUsedId(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<NoteEntity>)

    @Query("UPDATE notes SET position = :position WHERE id = :id")
    suspend fun setPosition(
        id: Long,
        position: Long,
    )

    @Query("UPDATE notes SET sync_failed = :failed WHERE id = :id")
    suspend fun setFailed(
        id: Long,
        failed: Boolean,
    )

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM notes")
    suspend fun deleteAll()

    @Query("SELECT * FROM id_alias")
    fun observeAliases(): Flow<List<IdAlias>>

    @Query("SELECT new_id FROM id_alias WHERE old_id = :id")
    suspend fun aliasOf(id: Long): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAlias(alias: IdAlias)

    @Query("DELETE FROM id_alias")
    suspend fun deleteAliases()

    @Query("SELECT value FROM meta WHERE `key` = :key")
    suspend fun meta(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMeta(meta: CacheMeta)

    @Query("DELETE FROM meta")
    suspend fun deleteMeta()
}
