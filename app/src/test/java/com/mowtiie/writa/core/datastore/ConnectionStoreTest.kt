package com.mowtiie.writa.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ConnectionStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(folder.root, "connection.preferences_pb") },
        )

    @Test
    fun isNotConfiguredWhenNothingIsStored() =
        runTest {
            val store = ConnectionStore(dataStore())

            assertFalse(store.isConfigured())
            assertNull(store.current())
        }

    @Test
    fun savesTheConnectionAndReadsItBack() =
        runTest {
            val store = ConnectionStore(dataStore())

            store.save("https://abc.supabase.co", "anon-key")

            assertTrue(store.isConfigured())
            assertEquals(Connection("https://abc.supabase.co", "anon-key"), store.current())
        }

    @Test
    fun saveTrimsAndStripsOneTrailingSlash() =
        runTest {
            val store = ConnectionStore(dataStore())

            store.save("  https://abc.supabase.co/  ", "  anon-key \n")

            assertEquals(Connection("https://abc.supabase.co", "anon-key"), store.current())
        }

    @Test
    fun saveStripsOnlyOneTrailingSlash() =
        runTest {
            val store = ConnectionStore(dataStore())

            store.save("https://abc.supabase.co//", "anon-key")

            assertEquals("https://abc.supabase.co/", store.current()?.baseUrl)
        }

    @Test
    fun emptyValuesAreNotConfigured() =
        runTest {
            val store = ConnectionStore(dataStore())

            store.save("", "anon-key")
            assertFalse(store.isConfigured())

            store.save("https://abc.supabase.co", "   ")
            assertFalse(store.isConfigured())

            store.save("/", "anon-key")
            assertFalse(store.isConfigured())
        }

    @Test
    fun aMissingKeyIsNotConfigured() =
        runTest {
            val dataStore = dataStore()
            dataStore.edit { it[stringPreferencesKey("base_url")] = "https://abc.supabase.co" }

            assertFalse(ConnectionStore(dataStore).isConfigured())
        }

    @Test
    fun clearForgetsTheConnection() =
        runTest {
            val store = ConnectionStore(dataStore())
            store.save("https://abc.supabase.co", "anon-key")

            store.clear()

            assertFalse(store.isConfigured())
            assertNull(store.connection.first())
        }
}
