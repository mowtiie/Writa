package com.mowtiie.writa.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class Connection(
    val baseUrl: String,
    val anonKey: String,
)

/** The user's Supabase project. Emits null until both the URL and the key are present and non-empty. */
@Singleton
class ConnectionStore
    @Inject
    constructor(
        @ConnectionDataStore private val dataStore: DataStore<Preferences>,
    ) {
        val connection: Flow<Connection?> = dataStore.data.map(::toConnection)

        suspend fun current(): Connection? = connection.first()

        suspend fun isConfigured(): Boolean = current() != null

        suspend fun save(
            baseUrl: String,
            anonKey: String,
        ) {
            dataStore.edit {
                it[BASE_URL] = baseUrl.trim().removeSuffix("/")
                it[ANON_KEY] = anonKey.trim()
            }
        }

        suspend fun clear() {
            dataStore.edit { it.clear() }
        }

        private fun toConnection(preferences: Preferences): Connection? {
            val baseUrl = preferences[BASE_URL]
            val anonKey = preferences[ANON_KEY]
            return if (baseUrl.isNullOrEmpty() || anonKey.isNullOrEmpty()) null else Connection(baseUrl, anonKey)
        }

        private companion object {
            val BASE_URL = stringPreferencesKey("base_url")
            val ANON_KEY = stringPreferencesKey("anon_key")
        }
    }
