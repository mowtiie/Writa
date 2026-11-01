package com.mowtiie.writa.feature.notes.sync

import com.mowtiie.writa.core.di.ApplicationScope
import com.mowtiie.writa.core.network.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Uploads what is waiting whenever the device comes online, which includes the moment the app starts online. */
@Singleton
class ReconnectSync
    @Inject
    constructor(
        private val networkMonitor: NetworkMonitor,
        private val syncEngine: SyncEngine,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        fun start() {
            scope.launch {
                networkMonitor.isOnline
                    .distinctUntilChanged()
                    .filter { it }
                    .collect { syncEngine.trigger() }
            }
        }
    }
