package com.mowtiie.writa.feature.trash

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

data class TrashUiState(
    val notes: List<Note> = emptyList(),
    /** False until the notes have been read once, so an empty list is not mistaken for an empty trash. */
    val isLoaded: Boolean = false,
    val actionFailed: Boolean = false,
)

/**
 * The notes in the trash, most recently trashed first. Nothing is removed automatically: a note leaves the trash
 * when the user restores it or deletes it for good, one at a time or all at once.
 */
@HiltViewModel
class TrashViewModel
    @Inject
    constructor(
        private val repository: NoteRepository,
    ) : ViewModel() {
        private val actionFailed = MutableStateFlow(false)

        val state: StateFlow<TrashUiState> =
            combine(repository.observeTrashed(), actionFailed) { notes, failed ->
                TrashUiState(notes.sortedByDescending { it.deletedAt }, isLoaded = true, actionFailed = failed)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TrashUiState())

        fun onRestore(id: Long) = change { repository.restore(id) != null }

        fun onDeleteForever(id: Long) = change { repository.deletePermanently(id) }

        fun onEmptyTrash() = change { repository.emptyTrash() }

        fun onMessageShown() = actionFailed.update { false }

        private fun change(block: suspend () -> Boolean) {
            viewModelScope.launch { if (!attempt(block)) actionFailed.value = true }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
