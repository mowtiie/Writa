package com.mowtiie.writa.feature.notes.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mowtiie.writa.core.datastore.SettingsStore
import com.mowtiie.writa.core.di.ApplicationScope
import com.mowtiie.writa.core.di.DefaultDispatcher
import com.mowtiie.writa.feature.auth.AuthRepository
import com.mowtiie.writa.feature.notes.NoteListQuery
import com.mowtiie.writa.feature.notes.NoteRepository
import com.mowtiie.writa.feature.notes.RefreshState
import com.mowtiie.writa.feature.notes.SearchableNotes
import com.mowtiie.writa.feature.notes.Sort
import com.mowtiie.writa.feature.notes.Tags
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject

/** A note that was swiped away and is waiting out the undo period before it goes to the trash. */
data class PendingDelete(
    val noteId: Long,
)

enum class NoteListMessage { ActionFailed }

data class NoteListUiState(
    val items: List<NoteListItem> = emptyList(),
    /** False until the notes have been read once, so an empty list is not mistaken for no notes. */
    val isLoaded: Boolean = false,
    val search: String = "",
    val isSearchOpen: Boolean = false,
    val tags: List<TagCount> = emptyList(),
    val sort: Sort = Sort.NEWEST,
    val isRefreshing: Boolean = false,
    val banner: NotesBanner? = null,
    val emptyReason: EmptyReason? = null,
    val pendingChanges: Int = 0,
    val pendingDelete: PendingDelete? = null,
    val message: NoteListMessage? = null,
)

/**
 * The notes list: what is shown follows the local database, so every change made here or by a refresh or upload
 * appears by itself. Search, tag filter and sort are applied on the device.
 *
 * Swiping a note away hides it and starts an undo period. The move to the trash is committed when the period ends,
 * when another note is swiped, or when the screen stops, and Undo within the period cancels it.
 */
@HiltViewModel
class NoteListViewModel
    @Inject
    constructor(
        private val savedState: SavedStateHandle,
        private val repository: NoteRepository,
        private val auth: AuthRepository,
        private val settingsStore: SettingsStore,
        @ApplicationScope private val applicationScope: CoroutineScope,
        @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val search = MutableStateFlow(savedState[KEY_SEARCH] ?: "")
        private val searchOpen = MutableStateFlow(savedState[KEY_SEARCH_OPEN] ?: false)
        private val selectedTags = MutableStateFlow(savedState.get<ArrayList<String>>(KEY_TAGS).orEmpty().toSet())
        private val pendingDelete = MutableStateFlow<PendingDelete?>(null)
        private val committing = MutableStateFlow<Set<Long>>(emptySet())
        private val message = MutableStateFlow<NoteListMessage?>(null)
        private var undoTimer: Job? = null

        private val sort: Flow<Sort> = settingsStore.settings.map { it.sort }.distinctUntilChanged()

        /** The notes the user can see: a note being swiped away, or on its way to the trash, is left out. */
        private val visible: Flow<Visible> =
            combine(repository.observeActive(), repository.observeStableKeys(), pendingDelete, committing) {
                notes,
                keys,
                pending,
                committing,
                ->
                val hidden = committing + listOfNotNull(pending?.noteId)
                Visible(notes.filterNot { it.id in hidden }.map { NoteListItem(keys[it.id] ?: it.id, it) }, pending)
            }

        /** The search text of every note is lowercased once per change of the notes, not once per keystroke. */
        private val indexed: Flow<Indexed> = visible.map(::index).flowOn(defaultDispatcher)

        private fun index(visible: Visible) =
            Indexed(visible.items, SearchableNotes(visible.items.map { it.note }), visible.pendingDelete)

        private val listing: Flow<Listing> =
            combine(indexed, search, selectedTags, sort) { indexed, search, selected, sort ->
                indexed.shape(search, selected, sort)
            }.flowOn(defaultDispatcher)

        private val chrome: Flow<Chrome> =
            combine(search, searchOpen, sort, message, ::Chrome)

        private val sync: Flow<Pair<RefreshState, Int>> =
            combine(
                repository.refreshState,
                repository.observePendingCount(),
            ) { refresh, pending -> refresh to pending }

        val state: StateFlow<NoteListUiState> =
            combine(listing, chrome, sync) { listing, chrome, (refresh, pendingChanges) ->
                NoteListUiState(
                    items = listing.items,
                    isLoaded = true,
                    search = chrome.search,
                    isSearchOpen = chrome.searchOpen,
                    tags = listing.tags,
                    sort = listing.sort,
                    isRefreshing = refresh is RefreshState.Loading,
                    banner = NotesBanner.of(refresh, pendingChanges),
                    emptyReason =
                        EmptyReason.of(
                            isLoaded = true,
                            totalNotes = listing.total,
                            shown = listing.items.size,
                            search = listing.search,
                            loadFailed = refresh is RefreshState.Failed,
                        ),
                    pendingChanges = pendingChanges,
                    pendingDelete = listing.pendingDelete,
                    message = chrome.message,
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                NoteListUiState(search = search.value, isSearchOpen = searchOpen.value),
            )

        init {
            refresh()
            viewModelScope.launch {
                indexed.map { it.tagKeys }.distinctUntilChanged().collect { existing -> pruneSelection(existing) }
            }
            viewModelScope.launch {
                repository.observeActive().collect { notes ->
                    forgetCommittedNotesThatAreGone(notes.mapTo(HashSet()) { it.id })
                }
            }
        }

        // ---------------------------- refreshing ----------------------------

        fun refresh() {
            viewModelScope.launch { repository.refresh() }
        }

        // ---------------------------- search, tags, sort ----------------------------

        fun onSearchOpen() {
            savedState[KEY_SEARCH_OPEN] = true
            searchOpen.value = true
        }

        fun onSearchChange(text: String) {
            savedState[KEY_SEARCH] = text
            search.value = text
        }

        /** Closing the search forgets what was typed. */
        fun onSearchClose() {
            savedState[KEY_SEARCH_OPEN] = false
            savedState[KEY_SEARCH] = ""
            searchOpen.value = false
            search.value = ""
        }

        fun onTagToggle(key: String) {
            setSelectedTags(if (key in selectedTags.value) selectedTags.value - key else selectedTags.value + key)
        }

        fun onClearTags() = setSelectedTags(emptySet())

        fun onSortSelected(sort: Sort) {
            viewModelScope.launch { settingsStore.setSort(sort) }
        }

        private fun setSelectedTags(keys: Set<String>) {
            savedState[KEY_TAGS] = ArrayList(keys)
            selectedTags.value = keys
        }

        /** A tag disappears with its last note, and must not come back selected if it is used again later. */
        private fun pruneSelection(existing: Set<String>) {
            val kept = selectedTags.value.filter { it in existing }.toSet()
            if (kept != selectedTags.value) setSelectedTags(kept)
        }

        // ---------------------------- swiping a note away ----------------------------

        fun onSwipeDelete(noteId: Long) {
            commitPendingDelete()
            pendingDelete.value = PendingDelete(noteId)
            undoTimer =
                viewModelScope.launch {
                    delay(UNDO_DURATION_MS)
                    commitPendingDelete()
                }
        }

        fun onUndo() {
            undoTimer?.cancel()
            undoTimer = null
            pendingDelete.value = null
        }

        /** The screen is no longer in front: a note waiting out its undo period goes to the trash now. */
        fun onScreenStopped() = commitPendingDelete()

        /** Ends the session and wipes the saved notes; a note still waiting out its undo period is not trashed. */
        fun signOut() {
            onUndo()
            applicationScope.launch {
                auth.signOut()
                repository.clear()
            }
        }

        private fun commitPendingDelete() {
            val pending = pendingDelete.value ?: return
            undoTimer?.cancel()
            undoTimer = null
            committing.update { it + pending.noteId }
            pendingDelete.value = null
            viewModelScope.launch {
                try {
                    repository.moveToTrash(pending.noteId)
                } catch (e: IOException) {
                    showAgain(pending.noteId)
                } catch (e: GeneralSecurityException) {
                    showAgain(pending.noteId)
                }
            }
        }

        /**
         * A note that was sent to the trash stays hidden until the database list has caught up. Un-hiding it when the
         * write returns would show it again for a moment, because the list is updated a little later.
         */
        private fun forgetCommittedNotesThatAreGone(listed: Set<Long>) {
            committing.update { ids -> ids.filterTo(HashSet()) { it in listed } }
        }

        /** Only a failed move brings the note back; a successful one stays hidden until the list stops containing it. */
        private fun showAgain(noteId: Long) {
            committing.update { it - noteId }
            message.value = NoteListMessage.ActionFailed
        }

        override fun onCleared() {
            // The screen is gone for good (for example the session ended); finish what the user asked for.
            pendingDelete.value?.let { pending ->
                applicationScope.launch {
                    try {
                        repository.moveToTrash(pending.noteId)
                    } catch (_: IOException) {
                        // Signed out or unwritable: the note simply stays where it was.
                    } catch (_: GeneralSecurityException) {
                        // Same.
                    }
                }
            }
        }

        // ---------------------------- actions on a note ----------------------------

        fun setPinned(
            noteId: Long,
            pinned: Boolean,
        ) = act { repository.setPinned(noteId, pinned) }

        fun archive(noteId: Long) = act { repository.setArchived(noteId, true) }

        fun moveToTrash(noteId: Long) = act { repository.moveToTrash(noteId) }

        fun retryFailed(noteId: Long) = act { repository.retryFailed(noteId) }

        fun discardFailed(noteId: Long) = act { repository.discardFailed(noteId) }

        fun onMessageShown() {
            message.value = null
        }

        private fun act(action: suspend () -> Unit) {
            viewModelScope.launch {
                try {
                    action()
                } catch (_: IOException) {
                    message.value = NoteListMessage.ActionFailed
                } catch (_: GeneralSecurityException) {
                    message.value = NoteListMessage.ActionFailed
                }
            }
        }

        // ---------------------------- shaping the list ----------------------------

        /** The notes the user can see, and the swipe that is waiting out its undo period, which hid one of them. */
        private class Visible(
            val items: List<NoteListItem>,
            val pendingDelete: PendingDelete?,
        )

        private class Chrome(
            val search: String,
            val searchOpen: Boolean,
            val sort: Sort,
            val message: NoteListMessage?,
        )

        /** The list together with the search and sort it was made for, so the screen never mixes old and new. */
        private class Listing(
            val items: List<NoteListItem>,
            val tags: List<TagCount>,
            val total: Int,
            val search: String,
            val sort: Sort,
            val pendingDelete: PendingDelete?,
        )

        private class Indexed(
            val items: List<NoteListItem>,
            val searchable: SearchableNotes,
            val pendingDelete: PendingDelete?,
        ) {
            private val usage: Map<String, Int> = Tags.usage(items.map { it.note.tags })

            val tagKeys: Set<String> = usage.keys.mapTo(HashSet(), Tags::key)

            fun shape(
                search: String,
                selectedKeys: Set<String>,
                sort: Sort,
            ): Listing {
                val effective = selectedKeys.filterTo(HashSet()) { it in tagKeys }
                val byId = items.associateBy { it.note.id }
                val shown = searchable.apply(NoteListQuery(search, effective, sort)).map { byId.getValue(it.id) }
                val tags = usage.map { (tag, count) -> TagCount(tag, Tags.key(tag), count, Tags.key(tag) in effective) }
                return Listing(shown, tags, items.size, search, sort, pendingDelete)
            }
        }

        companion object {
            const val UNDO_DURATION_MS = 6_000L
            private const val STOP_TIMEOUT_MS = 5_000L
            private const val KEY_SEARCH = "search"
            private const val KEY_SEARCH_OPEN = "searchOpen"
            private const val KEY_TAGS = "tags"
        }
    }
