package com.mowtiie.writa.feature.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mowtiie.writa.core.assets.AssetReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/** The licence as the dialog shows it: not asked for yet, loaded, or impossible to read. */
sealed interface LicenseState {
    data object Hidden : LicenseState

    data class Shown(
        val text: String,
    ) : LicenseState

    data object Unreadable : LicenseState
}

data class AboutUiState(
    val license: LicenseState = LicenseState.Hidden,
    val easterEgg: Boolean = false,
)

@HiltViewModel
class AboutViewModel
    @Inject
    constructor(
        private val assets: AssetReader,
    ) : ViewModel() {
        private val _state = MutableStateFlow(AboutUiState())
        val state: StateFlow<AboutUiState> = _state.asStateFlow()

        private var versionTaps = 0

        fun onLicenseOpen() {
            viewModelScope.launch {
                val license =
                    try {
                        LicenseState.Shown(assets.read(AssetReader.LICENSE))
                    } catch (_: IOException) {
                        LicenseState.Unreadable
                    }
                _state.update { it.copy(license = license) }
            }
        }

        fun onLicenseClose() = _state.update { it.copy(license = LicenseState.Hidden) }

        /** Tapping the version often enough shows a small message. */
        fun onVersionTap() {
            versionTaps++
            if (versionTaps == EASTER_EGG_TAPS) _state.update { it.copy(easterEgg = true) }
        }

        fun onEasterEggShown() = _state.update { it.copy(easterEgg = false) }

        private companion object {
            const val EASTER_EGG_TAPS = 7
        }
    }
