package com.mowtiie.writa.core.network

import com.mowtiie.writa.core.datastore.ConnectionStore
import com.mowtiie.writa.core.datastore.testPreferences
import com.mowtiie.writa.core.security.FakeSecureStorage
import com.mowtiie.writa.core.security.FakeSecureStorageFactory
import com.mowtiie.writa.core.security.Session
import com.mowtiie.writa.core.security.SessionStore
import com.mowtiie.writa.feature.notes.remote.NoteRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import java.io.File

/** A MockWebServer wired up like production: stored connection, signed-in session, authed client with refresh. */
class TestBackend(
    folder: File,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val server = MockWebServer()
    val connectionStore =
        ConnectionStore(
            testPreferences(scope, File(folder, "connection.preferences_pb")),
        )
    val sessionStore =
        SessionStore(FakeSecureStorageFactory(mutableListOf(FakeSecureStorage())), Dispatchers.Unconfined)
    val client: OkHttpClient
    val remote: NoteRemoteDataSource

    init {
        val plain = HttpClients.plain()
        client = HttpClients.authed(plain, TokenAuthenticator(connectionStore, sessionStore, plain, apiJson))
        remote = NoteRemoteDataSource(connectionStore, sessionStore, client, apiJson, Dispatchers.IO)
    }

    fun start() {
        server.start()
        runBlocking {
            connectionStore.save(server.url("/").toString(), ANON_KEY)
            sessionStore.save(Session("user-token", "refresh-1", "me@example.com"))
        }
    }

    fun close() {
        server.shutdown()
        scope.cancel()
    }

    companion object {
        const val ANON_KEY = "anon-key"
    }
}
