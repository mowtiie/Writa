package com.mowtiie.writa.feature.setup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mowtiie.writa.BuildConfig
import com.mowtiie.writa.core.datastore.ConnectionStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupUiState(
    val url: String = "",
    val key: String = "",
    val urlProblem: UrlProblem? = null,
    val keyProblem: KeyProblem? = null,
    val isLoading: Boolean = false,
)

enum class SetupEvent { FocusUrl, FocusKey }

/** What the fields start with. Only debug builds fill them in, from `local.properties`. */
data class SetupDefaults(
    val url: String,
    val key: String,
)

@Module
@InstallIn(SingletonComponent::class)
object SetupModule {
    @Provides
    fun provideSetupDefaults(): SetupDefaults = SetupDefaults(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY)
}

/**
 * Checks the server and key, and saves them. Saving is all that is needed to move on: the navigation follows the
 * stored connection, so this screen never navigates by itself.
 */
@HiltViewModel
class SetupViewModel
    @Inject
    constructor(
        private val savedState: SavedStateHandle,
        defaults: SetupDefaults,
        private val probe: ServerProbe,
        private val connectionStore: ConnectionStore,
    ) : ViewModel() {
        private val _state =
            MutableStateFlow(
                SetupUiState(
                    url = savedState[KEY_URL] ?: defaults.url,
                    key = savedState[KEY_KEY] ?: defaults.key,
                ),
            )
        private val _events = Channel<SetupEvent>(Channel.BUFFERED)

        val state: StateFlow<SetupUiState> = _state.asStateFlow()
        val events: Flow<SetupEvent> = _events.receiveAsFlow()

        fun onUrlChange(url: String) {
            savedState[KEY_URL] = url
            _state.update { it.copy(url = url, urlProblem = null) }
        }

        fun onKeyChange(key: String) {
            savedState[KEY_KEY] = key
            _state.update { it.copy(key = key, keyProblem = null) }
        }

        fun submit() {
            val current = _state.value
            if (current.isLoading) return

            val urlProblem = SetupInput.urlProblem(current.url)
            val keyProblem = SetupInput.keyProblem(current.key)
            if (urlProblem != null || keyProblem != null) {
                _state.update { it.copy(urlProblem = urlProblem, keyProblem = keyProblem) }
                _events.trySend(if (urlProblem != null) SetupEvent.FocusUrl else SetupEvent.FocusKey)
                return
            }

            _state.update { it.copy(isLoading = true, urlProblem = null, keyProblem = null) }
            val url = SetupInput.normalizeUrl(current.url)
            val key = SetupInput.normalizeKey(current.key)
            viewModelScope.launch {
                when (val result = probe.check(url, key)) {
                    ProbeResult.Reachable -> {
                        connectionStore.save(url, key)
                        _state.update { it.copy(isLoading = false) }
                    }

                    else -> {
                        showProblem(result)
                    }
                }
            }
        }

        private fun showProblem(result: ProbeResult) {
            val urlProblem =
                when (result) {
                    ProbeResult.InvalidUrl -> UrlProblem.Invalid
                    ProbeResult.NotReached -> UrlProblem.NotReached
                    ProbeResult.SecureConnectionProblem -> UrlProblem.SecureConnection
                    is ProbeResult.UnexpectedResponse -> UrlProblem.UnexpectedResponse(result.code)
                    is ProbeResult.KeyRejected, ProbeResult.Reachable -> null
                }
            val keyProblem = (result as? ProbeResult.KeyRejected)?.let { KeyProblem.Rejected(it.code) }
            _state.update { it.copy(isLoading = false, urlProblem = urlProblem, keyProblem = keyProblem) }
            _events.trySend(if (keyProblem != null) SetupEvent.FocusKey else SetupEvent.FocusUrl)
        }

        private companion object {
            const val KEY_URL = "url"
            const val KEY_KEY = "key"
        }
    }
