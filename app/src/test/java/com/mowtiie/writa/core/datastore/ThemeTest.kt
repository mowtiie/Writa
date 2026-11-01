package com.mowtiie.writa.core.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test
    fun systemFollowsTheSystem() {
        assertTrue(Theme.System.isDark(systemDark = true, batterySaver = false))
        assertFalse(Theme.System.isDark(systemDark = false, batterySaver = true))
    }

    @Test
    fun batteryIsDarkOnlyWhileBatterySaverIsOn() {
        assertTrue(Theme.Battery.isDark(systemDark = false, batterySaver = true))
        assertFalse(Theme.Battery.isDark(systemDark = true, batterySaver = false))
    }

    @Test
    fun lightAndDarkIgnoreEverythingElse() {
        for (system in listOf(true, false)) {
            for (saver in listOf(true, false)) {
                assertFalse(Theme.Light.isDark(system, saver))
                assertTrue(Theme.Dark.isDark(system, saver))
            }
        }
    }

    @Test
    fun theStoredKeysStayStable() {
        assertEquals(listOf("system", "battery", "light", "dark"), Theme.entries.map { it.key })
    }
}
