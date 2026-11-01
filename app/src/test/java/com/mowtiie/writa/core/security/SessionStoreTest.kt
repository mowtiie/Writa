package com.mowtiie.writa.core.security

import app.cash.turbine.test
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionStoreTest {
    private val stored = Session("access", "refresh", "me@example.com")

    private fun storageWith(session: Session?) =
        FakeSecureStorage().apply {
            if (session != null) {
                entries["access_token"] = session.accessToken
                entries["refresh_token"] = session.refreshToken
                session.email?.let { entries["user_email"] = it }
            }
        }

    private fun TestScope.storeFor(factory: FakeSecureStorageFactory) =
        SessionStore(factory, StandardTestDispatcher(testScheduler))

    @Test
    fun loadsTheStoredSession() =
        runTest {
            val store = storeFor(FakeSecureStorageFactory(mutableListOf(storageWith(stored))))

            assertEquals(stored, store.current())
        }

    @Test
    fun theSessionFlowStartsWithTheStoredSessionNeverWithNone() =
        runTest {
            val store = storeFor(FakeSecureStorageFactory(mutableListOf(storageWith(stored))))

            store.session.test {
                assertEquals(stored, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun noSessionWhenATokenIsMissing() =
        runTest {
            val withoutRefresh = storageWith(stored).apply { entries.remove("refresh_token") }
            val store = storeFor(FakeSecureStorageFactory(mutableListOf(withoutRefresh)))

            assertNull(store.current())
        }

    @Test
    fun saveStoresTheSessionAndNotifiesCollectors() =
        runTest {
            val storage = storageWith(null)
            val store = storeFor(FakeSecureStorageFactory(mutableListOf(storage)))

            store.session.test {
                assertNull(awaitItem())
                store.save(stored)
                assertEquals(stored, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals("access", storage.entries["access_token"])
            assertEquals("refresh", storage.entries["refresh_token"])
            assertEquals("me@example.com", storage.entries["user_email"])
        }

    @Test
    fun saveWithoutEmailRemovesTheOldEmail() =
        runTest {
            val storage = storageWith(stored)
            val store = storeFor(FakeSecureStorageFactory(mutableListOf(storage)))

            store.save(Session("a2", "r2", null))

            assertNull(storage.entries["user_email"])
        }

    @Test
    fun updateTokensKeepsTheEmail() =
        runTest {
            val storage = storageWith(stored)
            val store = storeFor(FakeSecureStorageFactory(mutableListOf(storage)))

            store.updateTokens("a2", "r2")

            assertEquals(Session("a2", "r2", "me@example.com"), store.current())
            assertEquals("a2", storage.entries["access_token"])
            assertEquals("r2", storage.entries["refresh_token"])
        }

    @Test
    fun clearRemovesTheSessionEverywhere() =
        runTest {
            val storage = storageWith(stored)
            val store = storeFor(FakeSecureStorageFactory(mutableListOf(storage)))

            store.session.test {
                assertEquals(stored, awaitItem())
                store.clear()
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(emptyMap<String, String>(), storage.entries)
        }

    @Test
    fun aBrokenKeyIsRecreatedOnceAndSignsTheUserOut() =
        runTest {
            val fresh = storageWith(null)
            val factory = FakeSecureStorageFactory(mutableListOf(null, fresh))
            val store = storeFor(factory)

            assertNull(store.current())
            store.save(stored)

            assertEquals(1, factory.destroyCalls)
            assertEquals(2, factory.openCalls)
            assertEquals("access", fresh.entries["access_token"])
        }

    @Test
    fun anUnusableKeystoreFallsBackToAMemoryOnlySession() =
        runTest {
            val factory = FakeSecureStorageFactory(mutableListOf(null, null))
            val store = storeFor(factory)

            store.save(stored)

            assertEquals(stored, store.current())
            assertEquals(1, factory.destroyCalls)
            assertEquals(2, factory.openCalls)
        }

    @Test
    fun clearWorksWithoutStorage() =
        runTest {
            val store = storeFor(FakeSecureStorageFactory(mutableListOf(null, null)))
            store.save(stored)

            store.clear()

            assertNull(store.current())
        }

    @Test
    fun aFailedWriteKeepsTheSessionInMemory() =
        runTest {
            val storage = storageWith(null).apply { failOnWrite = true }
            val store = storeFor(FakeSecureStorageFactory(mutableListOf(storage)))

            store.save(stored)

            assertEquals(stored, store.current())
            assertEquals(emptyMap<String, String>(), storage.entries)
        }

    @Test
    fun concurrentFirstUsersOpenTheStorageOnce() =
        runTest {
            val factory = FakeSecureStorageFactory(mutableListOf(storageWith(stored)))
            val store = storeFor(factory)

            val sessions = List(5) { async { store.current() } }.awaitAll()

            assertEquals(1, factory.openCalls)
            assertEquals(List(5) { stored }, sessions)
        }

    @Test
    fun warmUpOpensTheStorageOnce() =
        runTest {
            val factory = FakeSecureStorageFactory(mutableListOf(storageWith(stored)))
            val store = storeFor(factory)

            store.warmUp()
            store.warmUp()
            store.current()

            assertEquals(1, factory.openCalls)
        }
}
