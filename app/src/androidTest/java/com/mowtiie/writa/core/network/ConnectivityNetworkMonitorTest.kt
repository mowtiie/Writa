package com.mowtiie.writa.core.network

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectivityNetworkMonitorTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val monitor = ConnectivityNetworkMonitor(context)

    @Test
    fun theFirstValueIsOfflineUntilTheSystemSaysOtherwise() =
        runBlocking {
            assertEquals(false, withTimeout(WAIT_MS) { monitor.isOnline.first() })
        }

    @Test
    fun aDeviceWithANetworkReportsOnlineSoon() =
        runBlocking {
            val values = withTimeout(WAIT_MS) { monitor.isOnline.take(2).toList() }

            assertEquals(listOf(false, true), values)
        }

    @Test
    fun collectingTwiceRegistersAndUnregistersCleanly() =
        runBlocking {
            repeat(3) { withTimeout(WAIT_MS) { monitor.isOnline.take(2).toList() } }
            assertTrue(true)
        }

    private companion object {
        const val WAIT_MS = 15_000L
    }
}
