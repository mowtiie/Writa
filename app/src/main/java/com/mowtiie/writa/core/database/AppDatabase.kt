package com.mowtiie.writa.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The local copy of the notes plus the changes made offline that the server has not seen yet. Unsent changes live
 * here, so this database is not disposable: from the first release on, every version bump needs a real
 * `Migration` and a migration test, and a destructive fallback is never acceptable.
 */
@Database(
    entities = [NoteEntity::class, IdAlias::class, CacheMeta::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        const val FILE_NAME = "writa.db"
    }
}
