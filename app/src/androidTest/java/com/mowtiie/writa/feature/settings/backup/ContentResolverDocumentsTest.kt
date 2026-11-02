package com.mowtiie.writa.feature.settings.backup

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.io.IOException

class ContentResolverDocumentsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val documents = ContentResolverDocuments(context, Dispatchers.IO)
    private val file = File(context.cacheDir, "documents-test.json")

    @After
    fun tearDown() {
        file.delete()
    }

    private fun uri() = Uri.fromFile(file).toString()

    @Test
    fun whatIsWrittenCanBeReadBack() =
        runBlocking {
            documents.write(uri()) { it.write("hello".toByteArray()) }

            val text = documents.read(uri()) { String(it.readBytes()) }

            assertEquals("hello", text)
        }

    @Test
    fun aDocumentThatCannotBeOpenedForReadingIsAnIoError() {
        assertThrows(IOException::class.java) {
            runBlocking { documents.read(Uri.fromFile(File(context.cacheDir, "missing/none.json")).toString()) { } }
        }
    }
}
