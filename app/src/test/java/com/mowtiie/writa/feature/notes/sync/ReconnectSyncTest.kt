package com.mowtiie.writa.feature.notes.sync

import com.mowtiie.writa.core.network.NetworkMonitor
import com.mowtiie.writa.feature.notes.NotesFixture
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.QueueDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReconnectSyncTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val online = MutableStateFlow(false)
    private lateinit var fixture: NotesFixture
    private lateinit var reconnectSync: ReconnectSync

    private val server get() = fixture.server

    @Before
    fun setUp() {
        fixture = NotesFixture(folder.root).also { it.start() }
        (server.dispatcher as QueueDispatcher).setFailFast(MockResponse().setResponseCode(503))
        val monitor =
            object : NetworkMonitor {
                override val isOnline: Flow<Boolean> = online
            }
        reconnectSync = ReconnectSync(monitor, fixture.engine, fixture.scope)
    }

    @After
    fun tearDown() = fixture.close()

    private fun row(id: Long) =
        MockResponse().setBody(
            """[{"id":$id,"title":"t","content":null,"tags":[],"pinned":false,"archived":false,""" +
                """"created_at":"2026-11-01T10:00:00+00:00","updated_at":"2026-11-01T10:00:00+00:00",""" +
                """"deleted_at":null,"client_id":null}]""",
        )

    private fun createOffline() = runBlocking { fixture.cache.createLocal("t", null, emptyList(), false) }

    private fun raw(id: Long) = runBlocking { fixture.dao.get(id) }

    private fun awaitUploaded(id: Long) =
        runBlocking {
            withTimeout(WAIT_MS) {
                while (raw(id) !=
                    null
                ) {
                    delay(POLL_MS)
                }
            }
        }

    @Test
    fun comingBackOnlineUploadsWhatIsWaiting() {
        val note = createOffline()
        server.enqueue(row(55))
        reconnectSync.start()

        online.value = true

        awaitUploaded(note.id)
        assertNotNull(raw(55))
    }

    @Test
    fun startingTheAppOnlineUploadsWhatIsWaiting() {
        val note = createOffline()
        server.enqueue(row(55))
        online.value = true

        reconnectSync.start()

        awaitUploaded(note.id)
    }

    @Test
    fun beingOfflineUploadsNothing() {
        createOffline()
        reconnectSync.start()

        runBlocking { delay(SETTLE_MS) }

        assertEquals(0, server.requestCount)
    }

    @Test
    fun goingOfflineUploadsNothing() {
        reconnectSync.start()
        online.value = true
        runBlocking { delay(SETTLE_MS) }
        val before = server.requestCount
        createOffline()

        online.value = false
        runBlocking { delay(SETTLE_MS) }

        assertEquals(before, server.requestCount)
    }

    @Test
    fun everyReconnectUploadsAgain() {
        reconnectSync.start()
        val first = createOffline()
        server.enqueue(row(55))
        online.value = true
        awaitUploaded(first.id)

        online.value = false
        val second = createOffline()
        server.enqueue(row(56))
        online.value = true

        awaitUploaded(second.id)
        assertNotNull(raw(56))
        assertNull(raw(second.id))
    }

    private companion object {
        const val WAIT_MS = 10_000L
        const val POLL_MS = 20L
        const val SETTLE_MS = 400L
    }
}
