package com.mowtiie.writa.feature.notes.cache

import com.mowtiie.writa.core.database.CacheOwner
import com.mowtiie.writa.core.datastore.ConnectionStore
import com.mowtiie.writa.core.security.SessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Nobody is signed in on a configured server, so there is no cache to read or write. */
class CacheUnavailableException : IOException("The note cache is not available")

/** The account and server the cache currently belongs to, or null when nobody is signed in. */
interface CacheOwnerSource {
    val owner: Flow<String?>
}

@Singleton
class SessionCacheOwnerSource
    @Inject
    constructor(
        connectionStore: ConnectionStore,
        sessionStore: SessionStore,
    ) : CacheOwnerSource {
        override val owner: Flow<String?> =
            combine(connectionStore.connection, sessionStore.session) { connection, session ->
                val email = session?.email
                if (connection == null || email == null) null else CacheOwner.of(connection.baseUrl, email)
            }.distinctUntilChanged()
    }

fun interface ClientIdGenerator {
    fun next(): String
}

class UuidClientIdGenerator
    @Inject
    constructor() : ClientIdGenerator {
        override fun next(): String = UUID.randomUUID().toString()
    }
