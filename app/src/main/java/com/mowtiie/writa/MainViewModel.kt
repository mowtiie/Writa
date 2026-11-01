package com.mowtiie.writa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mowtiie.writa.core.datastore.AppSettings
import com.mowtiie.writa.core.datastore.ConnectionStore
import com.mowtiie.writa.core.datastore.SettingsStore
import com.mowtiie.writa.core.security.SessionStore
import com.mowtiie.writa.navigation.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * What the whole app depends on: where the user stands and how the app should look. Both are null until they have
 * been read, so nothing is drawn with the wrong theme or on the wrong screen first.
 */
@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        connectionStore: ConnectionStore,
        sessionStore: SessionStore,
        settingsStore: SettingsStore,
    ) : ViewModel() {
        val authState: StateFlow<AuthState?> =
            combine(connectionStore.connection, sessionStore.session, AuthState::of)
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        val settings: StateFlow<AppSettings?> =
            settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
