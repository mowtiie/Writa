package com.mowtiie.writa.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.mowtiie.writa.core.di.IoDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ConnectionDataStore

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    private const val SETTINGS_FILE = "settings"
    private const val CONNECTION_FILE = "connection"

    @Provides
    @Singleton
    @SettingsDataStore
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = create(context, SETTINGS_FILE, ioDispatcher)

    @Provides
    @Singleton
    @ConnectionDataStore
    fun provideConnectionDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = create(context, CONNECTION_FILE, ioDispatcher)

    private fun create(
        context: Context,
        name: String,
        ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> =
        PreferencesDataStores.create(
            scope = CoroutineScope(SupervisorJob() + ioDispatcher),
            produceFile = { context.preferencesDataStoreFile(name) },
        )
}
