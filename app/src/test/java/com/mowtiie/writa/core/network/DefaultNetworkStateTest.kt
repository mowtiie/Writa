package com.mowtiie.writa.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultNetworkStateTest {
    private val state = DefaultNetworkState()

    @Test
    fun startsOffline() {
        assertFalse(state.isOnline)
    }

    @Test
    fun aNetworkIsOnlineOnlyOnceItIsValidated() {
        state.onAvailable("wifi")
        assertFalse(state.isOnline)

        state.onCapabilitiesChanged("wifi", validated = true)

        assertTrue(state.isOnline)
    }

    @Test
    fun aNetworkThatDoesNotReachTheInternetIsOffline() {
        state.onAvailable("wifi")

        state.onCapabilitiesChanged("wifi", validated = false)

        assertFalse(state.isOnline)
    }

    @Test
    fun losingValidationGoesOfflineAndRegainingItComesBack() {
        state.onAvailable("wifi")
        state.onCapabilitiesChanged("wifi", validated = true)

        state.onCapabilitiesChanged("wifi", validated = false)
        assertFalse(state.isOnline)

        state.onCapabilitiesChanged("wifi", validated = true)
        assertTrue(state.isOnline)
    }

    @Test
    fun losingTheNetworkGoesOffline() {
        state.onAvailable("wifi")
        state.onCapabilitiesChanged("wifi", validated = true)

        state.onLost("wifi")

        assertFalse(state.isOnline)
    }

    @Test
    fun switchingNetworksFollowsTheNewOneWhateverTheOrderOfCallbacks() {
        state.onAvailable("wifi")
        state.onCapabilitiesChanged("wifi", validated = true)

        state.onAvailable("mobile")
        assertFalse(state.isOnline)
        state.onCapabilitiesChanged("mobile", validated = true)
        assertTrue(state.isOnline)

        state.onLost("wifi")
        assertTrue(state.isOnline)
    }

    @Test
    fun anOldNetworkLostAfterTheSwitchDoesNotTakeTheNewOneOffline() {
        state.onAvailable("wifi")
        state.onCapabilitiesChanged("wifi", validated = true)
        state.onAvailable("mobile")
        state.onCapabilitiesChanged("mobile", validated = true)

        state.onLost("wifi")

        assertTrue(state.isOnline)
    }

    @Test
    fun capabilitiesOfANetworkThatIsNotTheDefaultAreIgnored() {
        state.onAvailable("mobile")
        state.onCapabilitiesChanged("mobile", validated = true)

        state.onCapabilitiesChanged("wifi", validated = false)

        assertTrue(state.isOnline)
    }

    @Test
    fun capabilitiesForAnUnknownNetworkNeverMakeTheDeviceOnline() {
        state.onCapabilitiesChanged("wifi", validated = true)

        assertFalse(state.isOnline)
    }

    @Test
    fun aNetworkValidatedBeforeBeingMadeAvailableAgainStartsUnvalidated() {
        state.onAvailable("wifi")
        state.onCapabilitiesChanged("wifi", validated = true)
        state.onLost("wifi")

        state.onAvailable("wifi")

        assertFalse(state.isOnline)
    }
}
