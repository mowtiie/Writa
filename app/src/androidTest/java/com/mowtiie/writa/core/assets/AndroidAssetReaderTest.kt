package com.mowtiie.writa.core.assets

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AndroidAssetReaderTest {
    private val reader = AndroidAssetReader(InstrumentationRegistry.getInstrumentation().targetContext, Dispatchers.IO)

    @Test
    fun theLicenseShipsWithTheApp() =
        runBlocking {
            val license = reader.read(AssetReader.LICENSE)

            assertTrue(license.contains("GNU GENERAL PUBLIC LICENSE"))
        }

    @Test
    fun theSetupSqlShipsWithTheApp() =
        runBlocking {
            val sql = reader.read(AssetReader.SCHEMA)

            assertTrue(sql.contains("create table if not exists public.notes"))
            assertTrue(sql.contains("client_id"))
        }

    @Test
    fun aFileThatIsNotThereIsAnIoError() {
        assertThrows(IOException::class.java) { runBlocking { reader.read("nothing.txt") } }
    }
}
