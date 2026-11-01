package com.mowtiie.writa.feature.notes

import com.mowtiie.writa.core.database.CacheMeta
import com.mowtiie.writa.core.database.FakeNoteDao
import com.mowtiie.writa.core.database.PassThroughTransactionRunner
import com.mowtiie.writa.core.network.TestBackend
import com.mowtiie.writa.core.network.apiJson
import com.mowtiie.writa.core.security.FakeNoteCrypto
import com.mowtiie.writa.feature.notes.cache.CacheOwnerSource
import com.mowtiie.writa.feature.notes.cache.ClientIdGenerator
import com.mowtiie.writa.feature.notes.cache.NoteCache
import com.mowtiie.writa.feature.notes.sync.ConflictTitle
import com.mowtiie.writa.feature.notes.sync.SyncEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.Clock

/**
 * The real cache and sync engine over an in-memory DAO, talking to a MockWebServer through the real remote data
 * source. Runs on real threads, so tests using it call into it with `runBlocking`.
 */
class NotesFixture(
    folder: File,
    clock: Clock = Clock.systemUTC(),
    cacheDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val dao = FakeNoteDao()
    val crypto = FakeNoteCrypto()
    val codec = NoteCodec(crypto, apiJson)
    val owner = MutableStateFlow<String?>(OWNER)
    val backend = TestBackend(folder)
    val server get() = backend.server

    private var clientIdCounter = 0

    val cache =
        NoteCache(
            dao = dao,
            transactions = PassThroughTransactionRunner(),
            codec = codec,
            ownerSource =
                object : CacheOwnerSource {
                    override val owner = this@NotesFixture.owner
                },
            clock = clock,
            clientIds = ClientIdGenerator { "local-${++clientIdCounter}" },
            defaultDispatcher = cacheDispatcher,
        )

    val engine =
        SyncEngine(
            cache = cache,
            remote = backend.remote,
            conflictTitle = ConflictTitle { "$it (conflict)" },
            clientIds = ClientIdGenerator { "engine-${++clientIdCounter}" },
            scope = scope,
        )

    fun start() {
        backend.start()
        runBlocking { dao.putMeta(CacheMeta("owner", OWNER)) }
    }

    fun close() {
        scope.cancel()
        backend.close()
    }

    companion object {
        const val OWNER = "owner-a"
    }
}
