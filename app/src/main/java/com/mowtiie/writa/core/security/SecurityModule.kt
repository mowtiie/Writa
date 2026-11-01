package com.mowtiie.writa.core.security

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    private const val SESSION_PREFS = "writa_session"
    private const val SESSION_KEY_ALIAS = "writa_session_key"

    @Provides
    @Singleton
    fun provideSessionStorageFactory(
        @ApplicationContext context: Context,
    ): SecureStorageFactory = KeystoreSecureStorageFactory(context, SESSION_PREFS, SESSION_KEY_ALIAS)
}
