package com.mowtiie.writa.feature.notes

import kotlinx.coroutines.CancellationException

/** Runs a change to the saved notes; false if it did not work out or threw. Cancellation is not a failure. */
internal suspend fun attempt(block: suspend () -> Boolean): Boolean =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }
