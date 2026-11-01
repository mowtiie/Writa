package com.mowtiie.writa.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mowtiie.writa.core.ui.theme.Contrast
import com.mowtiie.writa.feature.notes.Sort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.dataStore(): DataStore<Preferences> =
        testPreferences(backgroundScope, File(folder.root, "settings.preferences_pb"))

    @Test
    fun defaultsApplyWhenNothingIsStored() =
        runTest {
            val store = SettingsStore(dataStore())

            assertEquals(AppSettings(), store.settings.first())
        }

    @Test
    fun everySettingIsStoredAndReadBack() =
        runTest {
            val store = SettingsStore(dataStore())

            store.setTheme(Theme.Dark)
            store.setContrast(Contrast.High)
            store.setDynamicColor(true)
            store.setScreenPrivacy(true)
            store.setSort(Sort.TITLE_ZA)

            val expected = AppSettings(Theme.Dark, Contrast.High, true, true, Sort.TITLE_ZA)
            assertEquals(expected, store.settings.first())
        }

    @Test
    fun unknownStoredValuesFallBackToDefaults() =
        runTest {
            val dataStore = dataStore()
            dataStore.edit {
                it[stringPreferencesKey("theme")] = "Battery Saving"
                it[stringPreferencesKey("contrast")] = "contrast_extreme"
                it[stringPreferencesKey("sort_mode")] = "OLDEST_FIRST"
            }

            assertEquals(AppSettings(), SettingsStore(dataStore).settings.first())
        }

    @Test
    fun storedKeysStayStable() =
        runTest {
            val dataStore = dataStore()
            val store = SettingsStore(dataStore)

            store.setTheme(Theme.Battery)
            store.setContrast(Contrast.Medium)
            store.setSort(Sort.TITLE_AZ)

            val stored = dataStore.data.first()
            assertEquals("battery", stored[stringPreferencesKey("theme")])
            assertEquals("contrast_medium", stored[stringPreferencesKey("contrast")])
            assertEquals("TITLE_AZ", stored[stringPreferencesKey("sort_mode")])
        }
}
