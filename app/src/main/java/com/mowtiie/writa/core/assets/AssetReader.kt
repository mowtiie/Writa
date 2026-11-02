package com.mowtiie.writa.core.assets

import android.content.Context
import com.mowtiie.writa.core.di.IoDispatcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** Reads text files that ship inside the app, such as the licence and `schema.sql`. */
interface AssetReader {
    /** The text of the asset [name]. Throws [IOException] if it cannot be read. */
    @Throws(IOException::class)
    suspend fun read(name: String): String

    companion object {
        const val LICENSE = "license.txt"
        const val SCHEMA = "schema.sql"
    }
}

class AndroidAssetReader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : AssetReader {
        override suspend fun read(name: String): String =
            withContext(ioDispatcher) {
                context.assets
                    .open(name)
                    .bufferedReader()
                    .use { it.readText() }
            }
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class AssetModule {
    @Binds
    abstract fun bindAssetReader(reader: AndroidAssetReader): AssetReader
}
