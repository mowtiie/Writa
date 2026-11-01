package com.mowtiie.writa.core.security

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.UUID

class KeystoreSecureStorageFactoryTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefsName = "test_secure_${UUID.randomUUID()}"
    private val keyAlias = "test_key_${UUID.randomUUID()}"
    private val factory = KeystoreSecureStorageFactory(context, prefsName, keyAlias)

    @Before
    fun startClean() = factory.destroy()

    @After
    fun cleanUp() = factory.destroy()

    private fun rawPreferences() = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    @Test
    fun storesAndReadsBackValues() {
        val storage = factory.open()

        storage.putAll(mapOf("access_token" to "abc", "user_email" to "me@example.com"))

        assertEquals("abc", storage.get("access_token"))
        assertEquals("me@example.com", storage.get("user_email"))
    }

    @Test
    fun valuesSurviveReopening() {
        factory.open().putAll(mapOf("access_token" to "abc"))

        assertEquals("abc", factory.open().get("access_token"))
    }

    @Test
    fun plaintextNeverReachesThePreferencesFile() {
        factory.open().putAll(mapOf("access_token" to "very-secret-token"))

        val raw = rawPreferences().all.values.joinToString()

        assertFalse(raw.contains("very-secret-token"))
    }

    @Test
    fun aCiphertextCannotBeMovedToAnotherEntry() {
        val storage = factory.open()
        storage.putAll(mapOf("access_token" to "abc"))
        val sealed = rawPreferences().getString("access_token", null)
        rawPreferences().edit().putString("refresh_token", sealed).commit()

        assertNull(storage.get("refresh_token"))
        assertEquals("abc", storage.get("access_token"))
    }

    @Test
    fun aNullValueRemovesTheEntry() {
        val storage = factory.open()
        storage.putAll(mapOf("user_email" to "me@example.com"))

        storage.putAll(mapOf("user_email" to null))

        assertNull(storage.get("user_email"))
    }

    @Test
    fun clearRemovesEverything() {
        val storage = factory.open()
        storage.putAll(mapOf("a" to "1", "b" to "2"))

        storage.clear()

        assertNull(storage.get("a"))
        assertNull(storage.get("b"))
    }

    @Test
    fun valuesReadAsAbsentWhenTheKeyIsLost() {
        factory.open().putAll(mapOf("access_token" to "abc"))
        KeystoreKeys.delete(keyAlias)

        assertNull(factory.open().get("access_token"))
    }

    @Test
    fun garbageInThePreferencesReadsAsAbsent() {
        val storage = factory.open()
        rawPreferences().edit().putString("access_token", "not a sealed value").commit()

        assertNull(storage.get("access_token"))
    }

    @Test
    fun destroyRemovesTheValuesAndTheKey() {
        factory.open().putAll(mapOf("access_token" to "abc"))

        factory.destroy()

        assertEquals(0, rawPreferences().all.size)
        assertNull(factory.open().get("access_token"))
    }
}
