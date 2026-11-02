package com.mowtiie.writa.feature.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mowtiie.writa.feature.notes.Note
import com.mowtiie.writa.feature.notes.NoteRepository
import com.mowtiie.writa.feature.notes.attempt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArchiveUiState(
    val notes: List<Note> = emptyList(),
    /** False until the notes have been read once, so an empty list is not mistaken for no archived notes. */
    val isLoaded: Boolean = false,
    val actionFailed: Boolean = false,
)

/** The archived notes, which follow the local database. A note can be unarchived or moved to the trash from here. */
@HiltViewModel
class ArchiveViewModel
    @Inject
    constructor(
        private val repository: NoteRepository,
    ) : ViewModel() {
        private val actionFailed = MutableStateFlow(false)

        val state: StateFlow<ArchiveUiState> =
            combine(repository.observeArchived(), actionFailed) { notes, failed ->
                ArchiveUiState(notes, isLoaded = true, actionFailed = failed)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ArchiveUiState())

        fun onUnarchive(id: Long) = change { repository.setArchived(id, false) != null }

        fun onMoveToTrash(id: Long) = change { repository.moveToTrash(id) != null }

        fun onMessageShown() = actionFailed.update { false }

        private fun change(block: suspend () -> Boolean) {
            viewModelScope.launch { if (!attempt(block)) actionFailed.value = true }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
