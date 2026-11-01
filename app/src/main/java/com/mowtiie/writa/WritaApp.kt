package com.mowtiie.writa

import android.app.Application
import android.content.Context
import com.mowtiie.writa.core.di.ApplicationScope
import com.mowtiie.writa.core.security.SessionStore
import com.mowtiie.writa.feature.crash.CrashHandler
import com.mowtiie.writa.feature.notes.sync.ReconnectSync
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class WritaApp : Application() {
    @Inject
    lateinit var sessionStore: SessionStore

    @Inject
    lateinit var reconnectSync: ReconnectSync

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashHandler.install(base)
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { sessionStore.warmUp() }
        reconnectSync.start()
    }
}
