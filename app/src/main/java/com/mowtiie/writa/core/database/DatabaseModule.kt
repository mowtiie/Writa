package com.mowtiie.writa.core.database

import android.content.Context
import androidx.room.Room
import com.mowtiie.writa.core.security.KeystoreNoteCrypto
import com.mowtiie.writa.core.security.NoteCrypto
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val CACHE_KEY_ALIAS = "writa_cache_key"

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.FILE_NAME).build()

    @Provides
    fun provideNoteDao(database: AppDatabase): NoteDao = database.noteDao()

    @Provides
    @Singleton
    fun provideNoteCrypto(): NoteCrypto = KeystoreNoteCrypto(CACHE_KEY_ALIAS)
}
