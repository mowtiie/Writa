package com.mowtiie.writa.feature.notes.cache

import com.mowtiie.writa.core.database.AppDatabase
import com.mowtiie.writa.core.database.RoomTransactionRunner
import com.mowtiie.writa.core.database.TransactionRunner
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NoteCacheBindings {
    @Binds
    abstract fun bindOwnerSource(source: SessionCacheOwnerSource): CacheOwnerSource

    @Binds
    abstract fun bindClientIds(generator: UuidClientIdGenerator): ClientIdGenerator

    companion object {
        @Provides
        @Singleton
        fun provideTransactionRunner(database: AppDatabase): TransactionRunner = RoomTransactionRunner(database)

        @Provides
        fun provideClock(): Clock = Clock.systemUTC()
    }
}
