package com.mowtiie.writa.core.database

import androidx.room.withTransaction

/** Runs several DAO calls as one all-or-nothing change. A seam so cache logic can be tested without Room. */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

class RoomTransactionRunner(
    private val database: AppDatabase,
) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = database.withTransaction { block() }
}
