package com.mowtiie.writa.core.security

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.UUID

class SessionStoreKeystoreTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val factory =
        KeystoreSecureStorageFactory(context, "test_session_${UUID.randomUUID()}", "test_key_${UUID.randomUUID()}")
    private val session = Session("access", "refresh", "me@example.com")

    private fun newStore() = SessionStore(factory, Dispatchers.IO)

    @Before
    fun startClean() = factory.destroy()

    @After
    fun cleanUp() = factory.destroy()

    @Test
    fun aSavedSessionSurvivesANewStoreInstance() =
        runTest {
            newStore().save(session)

            assertEquals(session, newStore().current())
        }

    @Test
    fun updatedTokensArePersistedWithTheEmail() =
        runTest {
            val first = newStore()
            first.save(session)

            first.updateTokens("access2", "refresh2")

            assertEquals(Session("access2", "refresh2", "me@example.com"), newStore().current())
        }

    @Test
    fun aClearedSessionStaysCleared() =
        runTest {
            val first = newStore()
            first.save(session)

            first.clear()

            assertNull(newStore().current())
        }

    @Test
    fun anEmptyStoreHasNoSession() =
        runTest {
            assertNull(newStore().current())
        }
}
