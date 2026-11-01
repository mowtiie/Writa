package com.mowtiie.writa.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import kotlinx.coroutines.CoroutineScope
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * `FileStorage` replaces a file with `File.renameTo`, which Windows refuses when the target exists, so a second write
 * fails on a Windows development machine. Okio moves files with replace semantics on every platform.
 */
fun testPreferences(
    scope: CoroutineScope,
    file: File,
): DataStore<Preferences> =
    PreferencesDataStores.create(
        scope,
        OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file.absoluteFile.toOkioPath() },
    )
