package com.mowtiie.writa.core.datastore

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** A damaged preferences file must never take the app down; it reads as empty and can be written again. */
class DamagedFileTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val garbage = byteArrayOf(0x0A, 0x7F, 0x08, 0x62, 0x61, 0x73, 0x65, 0x24, 0x28, 0x70, 0x72)

    private fun TestScope.damagedFile(name: String): File = folder.root.resolve(name).also { it.writeBytes(garbage) }

    private fun TestScope.store(file: File) = PreferencesDataStores.create(backgroundScope) { file }

    @Test
    fun aDamagedConnectionReadsAsNotSetUp() =
        runTest {
            val connection = ConnectionStore(store(damagedFile("connection.preferences_pb")))

            assertFalse(connection.isConfigured())
            assertNull(connection.current())
        }

    @Test
    fun theConnectionCanBeSavedAgainAfterDamage() =
        runTest {
            val connection = ConnectionStore(store(damagedFile("connection.preferences_pb")))
            connection.current()

            connection.save("https://abc.supabase.co", "key")

            assertEquals(Connection("https://abc.supabase.co", "key"), connection.current())
        }

    @Test
    fun damagedSettingsFallBackToTheDefaults() =
        runTest {
            val settings = SettingsStore(store(damagedFile("settings.preferences_pb")))

            assertEquals(AppSettings(), settings.settings.first())
        }

    @Test
    fun settingsCanBeChangedAgainAfterDamage() =
        runTest {
            val settings = SettingsStore(store(damagedFile("settings.preferences_pb")))
            settings.settings.first()

            settings.setTheme(Theme.Dark)

            assertEquals(Theme.Dark, settings.settings.first().theme)
        }

    @Test
    fun aHealthyFileIsLeftAlone() =
        runTest {
            val file = folder.root.resolve("settings.preferences_pb")
            val settings = SettingsStore(store(file))

            settings.setTheme(Theme.Light)

            assertTrue(file.length() > 0)
            assertEquals(Theme.Light, settings.settings.first().theme)
        }
}
