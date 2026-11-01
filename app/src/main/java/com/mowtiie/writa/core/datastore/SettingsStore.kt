package com.mowtiie.writa.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mowtiie.writa.core.ui.theme.Contrast
import com.mowtiie.writa.feature.notes.Sort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsStore
    @Inject
    constructor(
        @SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) {
        val settings: Flow<AppSettings> = dataStore.data.map(::toSettings)

        suspend fun setTheme(theme: Theme) = write { it[THEME] = theme.key }

        suspend fun setContrast(contrast: Contrast) = write { it[CONTRAST] = contrast.key }

        suspend fun setDynamicColor(enabled: Boolean) = write { it[DYNAMIC_COLOR] = enabled }

        suspend fun setScreenPrivacy(enabled: Boolean) = write { it[SCREEN_PRIVACY] = enabled }

        suspend fun setSort(sort: Sort) = write { it[SORT_MODE] = sort.name }

        private suspend fun write(change: (MutablePreferences) -> Unit) {
            dataStore.edit(change)
        }

        private fun toSettings(preferences: Preferences): AppSettings {
            val defaults = AppSettings()
            return AppSettings(
                theme = Theme.entries.firstOrNull { it.key == preferences[THEME] } ?: defaults.theme,
                contrast = Contrast.entries.firstOrNull { it.key == preferences[CONTRAST] } ?: defaults.contrast,
                dynamicColor = preferences[DYNAMIC_COLOR] ?: defaults.dynamicColor,
                screenPrivacy = preferences[SCREEN_PRIVACY] ?: defaults.screenPrivacy,
                sort = Sort.entries.firstOrNull { it.name == preferences[SORT_MODE] } ?: defaults.sort,
            )
        }

        private companion object {
            val THEME = stringPreferencesKey("theme")
            val CONTRAST = stringPreferencesKey("contrast")
            val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
            val SCREEN_PRIVACY = booleanPreferencesKey("screen_privacy")
            val SORT_MODE = stringPreferencesKey("sort_mode")
        }
    }
