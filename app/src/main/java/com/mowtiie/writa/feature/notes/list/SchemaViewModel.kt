package com.mowtiie.writa.feature.notes.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mowtiie.writa.core.assets.AssetReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/** The SQL the user has to run, or null while it is being read or if it cannot be read. */
data class SchemaUiState(
    val sql: String? = null,
    val unreadable: Boolean = false,
)

/** Reads `schema.sql`, which ships inside the app, for the screen that asks the user to run it. */
@HiltViewModel
class SchemaViewModel
    @Inject
    constructor(
        assets: AssetReader,
    ) : ViewModel() {
        private val _state = MutableStateFlow(SchemaUiState())
        val state: StateFlow<SchemaUiState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                _state.value =
                    try {
                        SchemaUiState(sql = assets.read(AssetReader.SCHEMA))
                    } catch (_: IOException) {
                        SchemaUiState(unreadable = true)
                    }
            }
        }
    }
