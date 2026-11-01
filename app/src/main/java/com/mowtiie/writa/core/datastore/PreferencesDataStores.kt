package com.mowtiie.writa.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import java.io.File

object PreferencesDataStores {
    /**
     * A damaged file reads as empty, so settings fall back to their defaults and the connection reads as not set up.
     * Without this, one bad file would crash the app on every start with no way for the user to recover.
     */
    fun create(
        scope: CoroutineScope,
        produceFile: () -> File,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
            produceFile = produceFile,
        )
}
