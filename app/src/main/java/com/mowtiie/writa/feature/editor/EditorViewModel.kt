package com.mowtiie.writa.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mowtiie.writa.feature.notes.Note
import com.mowtiie.writa.feature.notes.NoteRepository
import com.mowtiie.writa.feature.notes.attempt
import com.mowtiie.writa.navigation.EditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Creates or edits one note. Only the note's id comes in through the route; the note itself is read from the local
 * database. What the user typed survives rotation and process death, and the screen closes only after the note was
 * written locally, so a failed save keeps everything on screen.
 */
@HiltViewModel
class EditorViewModel
    @Inject
    constructor(
        private val savedState: SavedStateHandle,
        private val repository: NoteRepository,
    ) : ViewModel() {
        private var noteId = savedState.get<Long>(EditorRoute.NOTE_ID_KEY) ?: EditorRoute.NEW_NOTE
        private val hasDraft = savedState.get<Boolean>(KEY_EDITED) == true

        private val _state =
            MutableStateFlow(
                EditorUiState(
                    load = if (noteId == EditorRoute.NEW_NOTE) EditorLoad.Ready else EditorLoad.Loading,
                    isNew = noteId == EditorRoute.NEW_NOTE,
                    draft = if (hasDraft) restoredDraft() else NoteDraft(),
                    tagInput = savedState[KEY_TAG_INPUT] ?: "",
                    archived = savedState[KEY_ARCHIVED] ?: false,
                ),
            )
        val state: StateFlow<EditorUiState> = _state.asStateFlow()

        private val _events = Channel<EditorEvent>(Channel.BUFFERED)
        val events: Flow<EditorEvent> = _events.receiveAsFlow()

        init {
            if (!_state.value.isNew) viewModelScope.launch { load() }
            viewModelScope.launch {
                repository.observeKnownTags().collect { tags -> _state.update { it.copy(knownTags = tags) } }
            }
        }

        private suspend fun load() {
            noteId = repository.resolveId(noteId)
            val note = repository.get(noteId)
            if (note == null) {
                _state.update { it.copy(load = EditorLoad.Unavailable) }
                return
            }
            val stored = note.toDraft()
            _state.update {
                it.copy(
                    load = EditorLoad.Ready,
                    original = stored,
                    draft = if (hasDraft) it.draft else stored,
                    archived = if (hasDraft) it.archived else note.archived,
                )
            }
        }

        fun onTitleChange(title: String) = edit { it.copy(draft = it.draft.copy(title = title), titleMissing = false) }

        fun onContentChange(content: String) = edit { it.copy(draft = it.draft.copy(content = content)) }

        fun onPinToggle() = edit { it.copy(draft = it.draft.copy(pinned = !it.draft.pinned)) }

        fun onTagInputChange(text: String) {
            edit { it.copy(tagInput = text, tagLimitReached = false) }
            if (',' in text) onTagCommit()
        }

        fun onTagCommit() =
            edit {
                val commit = TagEntry.commit(it.draft.tags, it.tagInput)
                it.copy(
                    draft = it.draft.copy(tags = commit.tags),
                    tagInput = commit.rejected.orEmpty(),
                    tagLimitReached = commit.rejected != null,
                )
            }

        fun onTagPicked(tag: String) {
            onTagInputChange(tag)
            onTagCommit()
        }

        fun onTagRemove(tag: String) =
            edit {
                it.copy(draft = it.draft.copy(tags = it.draft.tags - tag), tagLimitReached = false)
            }

        fun onSave() = save(archived = _state.value.archived)

        fun onArchiveToggle() = save(archived = !_state.value.archived)

        fun onTrash() {
            val current = _state.value
            if (current.isSaving) return
            if (current.isNew) {
                _events.trySend(EditorEvent.Close)
                return
            }
            _state.update { it.copy(isSaving = true) }
            viewModelScope.launch {
                val trashed = attempt { repository.moveToTrash(repository.resolveId(noteId)) != null }
                finish(trashed, EditorMessage.TrashFailed)
            }
        }

        fun onMessageShown() = _state.update { it.copy(message = null) }

        private fun save(archived: Boolean) {
            val current = _state.value
            if (current.isSaving) return
            if (current.draft.title.isBlank()) {
                _state.update { it.copy(titleMissing = true) }
                return
            }
            _state.update { it.copy(isSaving = true, titleMissing = false) }
            val draft = current.draft.copy(tags = current.tagsToSave)
            viewModelScope.launch {
                val saved = attempt { if (current.isNew) create(draft) else update(draft, archived) }
                finish(saved, EditorMessage.SaveFailed)
            }
        }

        private suspend fun create(draft: NoteDraft): Boolean {
            repository.create(draft.title.trim(), draft.content.trim().ifEmpty { null }, draft.tags, draft.pinned)
            return true
        }

        private suspend fun update(
            draft: NoteDraft,
            archived: Boolean,
        ): Boolean {
            val stored = repository.get(repository.resolveId(noteId)) ?: return false
            val edited =
                stored.copy(
                    title = draft.title.trim(),
                    content = draft.content.trim().ifEmpty { null },
                    tags = draft.tags,
                    pinned = draft.pinned,
                    archived = archived,
                )
            return repository.save(edited) != null
        }

        private fun finish(
            succeeded: Boolean,
            failure: EditorMessage,
        ) {
            if (succeeded) {
                _state.update { it.copy(isSaving = false) }
                _events.trySend(EditorEvent.Close)
            } else {
                _state.update { it.copy(isSaving = false, message = failure) }
            }
        }

        private fun edit(change: (EditorUiState) -> EditorUiState) {
            _state.update(change)
            keepDraft(_state.value)
        }

        private fun keepDraft(state: EditorUiState) {
            savedState[KEY_EDITED] = true
            savedState[KEY_TITLE] = state.draft.title
            savedState[KEY_CONTENT] = state.draft.content
            savedState[KEY_TAGS] = ArrayList(state.draft.tags)
            savedState[KEY_PINNED] = state.draft.pinned
            savedState[KEY_TAG_INPUT] = state.tagInput
            savedState[KEY_ARCHIVED] = state.archived
        }

        private fun restoredDraft() =
            NoteDraft(
                title = savedState[KEY_TITLE] ?: "",
                content = savedState[KEY_CONTENT] ?: "",
                tags = savedState.get<ArrayList<String>>(KEY_TAGS).orEmpty(),
                pinned = savedState[KEY_PINNED] ?: false,
            )

        private fun Note.toDraft() = NoteDraft(title, content.orEmpty(), tags, pinned)

        private companion object {
            const val KEY_EDITED = "edited"
            const val KEY_TITLE = "title"
            const val KEY_CONTENT = "content"
            const val KEY_TAGS = "tags"
            const val KEY_PINNED = "pinned"
            const val KEY_TAG_INPUT = "tag_input"
            const val KEY_ARCHIVED = "archived"
        }
    }
