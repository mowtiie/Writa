package com.mowtiie.writa.feature.settings.backup

import android.content.Context
import android.net.Uri
import com.mowtiie.writa.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/** Opens a document the user picked with the system file picker, on the IO dispatcher, and closes it afterwards. */
interface DocumentOpener {
    suspend fun write(
        uri: String,
        block: suspend (OutputStream) -> Unit,
    )

    suspend fun <T> read(
        uri: String,
        block: suspend (InputStream) -> T,
    ): T
}

class ContentResolverDocuments
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : DocumentOpener {
        override suspend fun write(
            uri: String,
            block: suspend (OutputStream) -> Unit,
        ) {
            withContext(ioDispatcher) {
                val out =
                    context.contentResolver.openOutputStream(Uri.parse(uri))
                        ?: throw IOException("Cannot open the file")
                out.use { block(it) }
            }
        }

        override suspend fun <T> read(
            uri: String,
            block: suspend (InputStream) -> T,
        ): T =
            withContext(ioDispatcher) {
                val input =
                    context.contentResolver.openInputStream(Uri.parse(uri)) ?: throw IOException("Cannot open the file")
                input.use { block(it) }
            }
    }
