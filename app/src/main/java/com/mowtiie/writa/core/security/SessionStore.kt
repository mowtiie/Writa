package com.mowtiie.writa.core.security

import com.mowtiie.writa.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

data class Session(
    val accessToken: String,
    val refreshToken: String,
    val email: String?,
)

/**
 * The signed-in session. Once the encrypted storage has been opened, the in-memory copy is the source of truth.
 * If the device keystore is unusable the session lasts until the process dies, and startup never fails.
 */
@Singleton
class SessionStore
    @Inject
    constructor(
        private val storageFactory: SecureStorageFactory,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        private val mutex = Mutex()
        private val state = MutableStateFlow<Session?>(null)
        private var storage: SecureStorage? = null

        @Volatile
        private var opened = false

        /** Emits nothing until storage has been read, so a stored session is never mistaken for none. */
        val session: Flow<Session?> =
            flow {
                ensureOpened()
                emitAll(state)
            }

        suspend fun warmUp() = ensureOpened()

        suspend fun current(): Session? {
            ensureOpened()
            return state.value
        }

        suspend fun save(session: Session) =
            mutex.withLock {
                openLocked()
                state.value = session
                persist(session.toEntries())
            }

        /** Replaces the tokens and keeps the signed-in email. */
        suspend fun updateTokens(
            accessToken: String,
            refreshToken: String,
        ) = mutex.withLock {
            openLocked()
            val updated = Session(accessToken, refreshToken, state.value?.email)
            state.value = updated
            persist(updated.toEntries())
        }

        suspend fun clear() =
            mutex.withLock {
                openLocked()
                state.value = null
                storage?.clear()
            }

        private suspend fun ensureOpened() {
            if (!opened) mutex.withLock { openLocked() }
        }

        private suspend fun openLocked() {
            if (opened) return
            withContext(ioDispatcher) {
                storage = openStorage()
                state.value = storage?.let(::readSession)
            }
            opened = true
        }

        private fun openStorage(): SecureStorage? =
            try {
                storageFactory.open()
            } catch (_: GeneralSecurityException) {
                // A broken keystore entry is the usual cause: start over with a fresh key, which signs the user out.
                storageFactory.destroy()
                try {
                    storageFactory.open()
                } catch (_: GeneralSecurityException) {
                    null
                }
            }

        private fun readSession(storage: SecureStorage): Session? {
            val access = storage.get(ACCESS_TOKEN) ?: return null
            val refresh = storage.get(REFRESH_TOKEN) ?: return null
            return Session(access, refresh, storage.get(EMAIL))
        }

        private fun persist(entries: Map<String, String?>) {
            try {
                storage?.putAll(entries)
            } catch (_: GeneralSecurityException) {
                // Not persisted; the session still works for this process.
            }
        }

        private fun Session.toEntries(): Map<String, String?> =
            mapOf(ACCESS_TOKEN to accessToken, REFRESH_TOKEN to refreshToken, EMAIL to email)

        private companion object {
            const val ACCESS_TOKEN = "access_token"
            const val REFRESH_TOKEN = "refresh_token"
            const val EMAIL = "user_email"
        }
    }
