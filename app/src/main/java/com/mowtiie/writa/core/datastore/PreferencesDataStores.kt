package com.mowtiie.writa.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Storage
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import java.io.File

/**
 * A damaged file reads as empty, so settings fall back to their defaults and the connection reads as not set up.
 * Without this, one bad file would crash the app on every start with no way for the user to recover.
 */
object PreferencesDataStores {
    private val corruptionHandler = ReplaceFileCorruptionHandler<Preferences> { emptyPreferences() }

    fun create(
        scope: CoroutineScope,
        produceFile: () -> File,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = corruptionHandler,
            scope = scope,
            produceFile = produceFile,
        )

    internal fun create(
        scope: CoroutineScope,
        storage: Storage<Preferences>,
    ): DataStore<Preferences> =
        DataStoreFactory.create(storage = storage, corruptionHandler = corruptionHandler, scope = scope)
}
