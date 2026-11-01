package com.mowtiie.writa.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

interface NetworkMonitor {
    /** True while the device has a default network that has been checked to reach the internet. */
    val isOnline: Flow<Boolean>
}

/**
 * What the default-network callbacks add up to. Callbacks can arrive in any order when the default network
 * changes (Wi-Fi to mobile data), so the state follows the current network only and ignores the others.
 */
internal class DefaultNetworkState {
    private var current: Any? = null
    private var validated = false

    val isOnline: Boolean get() = current != null && validated

    fun onAvailable(network: Any) {
        current = network
        validated = false
    }

    fun onCapabilitiesChanged(
        network: Any,
        validated: Boolean,
    ) {
        if (network == current) this.validated = validated
    }

    fun onLost(network: Any) {
        if (network == current) {
            current = null
            validated = false
        }
    }
}

@Singleton
class ConnectivityNetworkMonitor
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : NetworkMonitor {
        private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

        override val isOnline: Flow<Boolean> =
            callbackFlow {
                val state = DefaultNetworkState()
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            state.onAvailable(network)
                            trySend(state.isOnline)
                        }

                        override fun onCapabilitiesChanged(
                            network: Network,
                            capabilities: NetworkCapabilities,
                        ) {
                            state.onCapabilitiesChanged(
                                network,
                                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                            )
                            trySend(state.isOnline)
                        }

                        override fun onLost(network: Network) {
                            state.onLost(network)
                            trySend(state.isOnline)
                        }
                    }
                trySend(false)
                connectivityManager.registerDefaultNetworkCallback(callback)
                awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
            }.distinctUntilChanged()
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkMonitorModule {
    @Binds
    abstract fun bindNetworkMonitor(monitor: ConnectivityNetworkMonitor): NetworkMonitor
}
