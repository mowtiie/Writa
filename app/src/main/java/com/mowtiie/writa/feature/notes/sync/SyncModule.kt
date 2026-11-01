package com.mowtiie.writa.feature.notes.sync

import android.content.Context
import com.mowtiie.writa.R
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {
    @Provides
    fun provideConflictTitle(
        @ApplicationContext context: Context,
    ): ConflictTitle = ConflictTitle { title -> context.getString(R.string.note_conflict_copy_title, title) }
}
