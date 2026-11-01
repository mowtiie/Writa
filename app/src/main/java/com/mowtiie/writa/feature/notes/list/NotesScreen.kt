package com.mowtiie.writa.feature.notes.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mowtiie.writa.R
import com.mowtiie.writa.core.ui.ScrollToTopWhenFirstItemChanges
import com.mowtiie.writa.feature.notes.Note
import com.mowtiie.writa.feature.notes.RefreshFailure
import com.mowtiie.writa.feature.notes.Sort
import com.mowtiie.writa.feature.notes.SyncState

/** Where the list can take the user. */
class NotesNavigation(
    val onOpenNote: (Long) -> Unit = {},
    val onCreateNote: () -> Unit = {},
    val onOpenArchive: () -> Unit = {},
    val onOpenTrash: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenAbout: () -> Unit = {},
)

/** What the user can do on the list, apart from going elsewhere. */
class NotesActions(
    val onRefresh: () -> Unit = {},
    val onSearchOpen: () -> Unit = {},
    val onSearchChange: (String) -> Unit = {},
    val onSearchClose: () -> Unit = {},
    val onTagToggle: (String) -> Unit = {},
    val onSortSelected: (Sort) -> Unit = {},
    val onSwipeDelete: (Long) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onSetPinned: (Long, Boolean) -> Unit = { _, _ -> },
    val onArchive: (Long) -> Unit = {},
    val onMoveToTrash: (Long) -> Unit = {},
    val onRetryFailed: (Long) -> Unit = {},
    val onDiscardFailed: (Long) -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@Composable
fun NotesDestination(
    navigation: NotesNavigation,
    viewModel: NoteListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onScreenStopped() }
    val actions =
        remember(viewModel) {
            NotesActions(
                onRefresh = viewModel::refresh,
                onSearchOpen = viewModel::onSearchOpen,
                onSearchChange = viewModel::onSearchChange,
                onSearchClose = viewModel::onSearchClose,
                onTagToggle = viewModel::onTagToggle,
                onSortSelected = viewModel::onSortSelected,
                onSwipeDelete = viewModel::onSwipeDelete,
                onUndo = viewModel::onUndo,
                onSetPinned = viewModel::setPinned,
                onArchive = viewModel::archive,
                onMoveToTrash = viewModel::moveToTrash,
                onRetryFailed = viewModel::retryFailed,
                onDiscardFailed = viewModel::discardFailed,
                onMessageShown = viewModel::onMessageShown,
            )
        }
    NotesScreen(state, actions, navigation)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    state: NoteListUiState,
    actions: NotesActions,
    navigation: NotesNavigation,
) {
    val snackbarHost = remember { SnackbarHostState() }
    UndoSnackbar(snackbarHost, state.pendingDelete, actions.onUndo)
    MessageSnackbar(snackbarHost, state.message, actions.onMessageShown)

    var showSort by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            if (state.isSearchOpen) {
                SearchBar(state.search, actions.onSearchChange, actions.onSearchClose)
            } else {
                NotesTopBar(actions.onSearchOpen, { showSort = true }, navigation)
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = navigation.onCreateNote) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.notes_add_note))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            state.banner?.let { SavedNotesBanner(it, actions.onRefresh) }
            if (state.tags.isNotEmpty()) TagBar(state.tags, actions.onTagToggle)
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = actions.onRefresh,
                modifier = Modifier.weight(1f),
            ) {
                NoteList(state, actions, navigation)
            }
        }
    }

    if (showSort) {
        SortDialog(current = state.sort, onSelected = { actions.onSortSelected(it) }, onDismiss = { showSort = false })
    }
}

// ---------------------------- the bars ----------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesTopBar(
    onSearch: () -> Unit,
    onSort: () -> Unit,
    navigation: NotesNavigation,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(stringResource(R.string.app_name), modifier = Modifier.semantics { heading() }) },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.notes_menu_search))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.notes_menu_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    val entries =
                        listOf(
                            R.string.notes_menu_sort to onSort,
                            R.string.notes_menu_archive to navigation.onOpenArchive,
                            R.string.notes_menu_trash to navigation.onOpenTrash,
                            R.string.notes_menu_settings to navigation.onOpenSettings,
                            R.string.notes_menu_about to navigation.onOpenAbout,
                        )
                    for ((label, action) in entries) {
                        DropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            onClick = {
                                menuOpen = false
                                action()
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun SearchBar(
    search: String,
    onChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // The cursor belongs at the end of what was typed, also when the text comes back after a rotation.
    var field by remember { mutableStateOf(TextFieldValue(search, TextRange(search.length))) }
    if (field.text != search) field = TextFieldValue(search, TextRange(search.length))
    TextField(
        value = field,
        onValueChange = {
            field = it
            if (it.text != search) onChange(it.text)
        },
        placeholder = { Text(stringResource(R.string.notes_search_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {}),
        trailingIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.notes_search_close))
            }
        },
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        modifier = Modifier.fillMaxWidth().windowInsetsPadding(TopAppBarDefaults.windowInsets).focusRequester(focus),
    )
}

@Composable
private fun SavedNotesBanner(
    banner: NotesBanner,
    onRetry: () -> Unit,
) {
    val text =
        when (banner) {
            is NotesBanner.UnsentChanges -> {
                pluralStringResource(
                    R.plurals.notes_banner_unsent,
                    banner.count,
                    banner.count,
                )
            }

            is NotesBanner.RefreshFailed -> {
                stringResource(bannerText(banner.reason))
            }
        }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer)
                .combinedClickable(onClick = onRetry)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

private fun bannerText(reason: RefreshFailure): Int =
    when (reason) {
        RefreshFailure.Offline -> R.string.notes_banner_offline
        RefreshFailure.Server -> R.string.notes_banner_server
        RefreshFailure.SecureConnection -> R.string.notes_banner_secure
        RefreshFailure.Unavailable -> R.string.notes_banner_unavailable
        RefreshFailure.SchemaOutdated -> R.string.notes_banner_schema
        RefreshFailure.Other -> R.string.notes_banner_other
    }

@Composable
private fun TagBar(
    tags: List<TagCount>,
    onToggle: (String) -> Unit,
) {
    val description = stringResource(R.string.notes_filter_by_tag)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
    ) {
        items(tags, key = { it.key }) { tag ->
            FilterChip(
                selected = tag.selected,
                onClick = { onToggle(tag.key) },
                label = { Text(stringResource(R.string.notes_tag_with_count, tag.tag, tag.count)) },
            )
        }
    }
}

@Composable
private fun SortDialog(
    current: Sort,
    onSelected: (Sort) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notes_sort_title)) },
        text = {
            Column {
                for (sort in Sort.entries) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = sort == current,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onSelected(sort)
                                        onDismiss()
                                    },
                                ).padding(vertical = 8.dp),
                    ) {
                        RadioButton(selected = sort == current, onClick = null)
                        Text(stringResource(sortLabel(sort)), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.notes_confirm_cancel)) } },
    )
}

private fun sortLabel(sort: Sort): Int =
    when (sort) {
        Sort.NEWEST -> R.string.notes_sort_newest
        Sort.OLDEST -> R.string.notes_sort_oldest
        Sort.TITLE_AZ -> R.string.notes_sort_title_az
        Sort.TITLE_ZA -> R.string.notes_sort_title_za
    }

// ---------------------------- snackbars ----------------------------

@Composable
private fun UndoSnackbar(
    host: SnackbarHostState,
    pending: PendingDelete?,
    onUndo: () -> Unit,
) {
    val message = stringResource(R.string.notes_trashed)
    val undo = stringResource(R.string.notes_undo)
    LaunchedEffect(pending) {
        if (pending == null) return@LaunchedEffect
        // The ViewModel decides when the period ends; the snackbar only has to stay until then.
        val result =
            host.showSnackbar(
                message,
                actionLabel = undo,
                duration = androidx.compose.material3.SnackbarDuration.Indefinite,
            )
        if (result == SnackbarResult.ActionPerformed) onUndo()
    }
}

@Composable
private fun MessageSnackbar(
    host: SnackbarHostState,
    message: NoteListMessage?,
    onShown: () -> Unit,
) {
    val text = message?.let { stringResource(R.string.notes_action_failed) }
    LaunchedEffect(message) {
        if (text != null) {
            host.showSnackbar(text)
            onShown()
        }
    }
}

// ---------------------------- the list ----------------------------

@Composable
private fun NoteList(
    state: NoteListUiState,
    actions: NotesActions,
    navigation: NotesNavigation,
) {
    val listState = rememberLazyListState()
    ScrollToTopWhenFirstItemChanges(listState, state.items.firstOrNull()?.key)
    val dates = remember { NoteDates() }

    state.emptyReason?.let { EmptyMessage(it) }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.items, key = { it.key }) { item ->
            SwipeableNote(item.note, dates, actions, navigation)
        }
    }
}

@Composable
private fun EmptyMessage(reason: EmptyReason) {
    val text =
        when (reason) {
            EmptyReason.NoNotes -> stringResource(R.string.notes_empty)
            is EmptyReason.NoMatchForSearch -> stringResource(R.string.notes_empty_search, reason.search)
            EmptyReason.NoMatchForTags -> stringResource(R.string.notes_empty_tags)
        }
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

private enum class Confirm { Trash, Discard }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SwipeableNote(
    note: Note,
    dates: NoteDates,
    actions: NotesActions,
    navigation: NotesNavigation,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    var menuOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    val pin = stringResource(if (note.pinned) R.string.notes_action_unpin else R.string.notes_action_pin)
    val archive = stringResource(R.string.notes_action_archive)
    val trash = stringResource(R.string.notes_action_trash)
    val retry = stringResource(R.string.notes_action_retry)
    val discard = stringResource(R.string.notes_action_discard)
    val failed = note.syncState == SyncState.Failed

    SwipeToDismissBox(
        state = dismissState,
        onDismiss = { actions.onSwipeDelete(note.id) },
        backgroundContent = {
            // Only drawn while the row is being swiped, and left out of the accessibility tree: the screen reader
            // offers the same action on the row itself.
            if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 24.dp)
                        .clearAndSetSemantics {},
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(trash, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        },
    ) {
        Box {
            NoteCard(
                note = note,
                dates = dates,
                modifier =
                    Modifier
                        .combinedClickable(
                            onClick = { navigation.onOpenNote(note.id) },
                            onLongClick = { menuOpen = true },
                        ).semantics {
                            customActions =
                                buildList {
                                    add(
                                        CustomAccessibilityAction(trash) {
                                            actions.onSwipeDelete(note.id)
                                            true
                                        },
                                    )
                                    add(
                                        CustomAccessibilityAction(pin) {
                                            actions.onSetPinned(note.id, !note.pinned)
                                            true
                                        },
                                    )
                                    add(
                                        CustomAccessibilityAction(archive) {
                                            actions.onArchive(note.id)
                                            true
                                        },
                                    )
                                    if (failed) {
                                        add(
                                            CustomAccessibilityAction(retry) {
                                                actions.onRetryFailed(note.id)
                                                true
                                            },
                                        )
                                        add(
                                            CustomAccessibilityAction(discard) {
                                                confirm = Confirm.Discard
                                                true
                                            },
                                        )
                                    }
                                }
                        },
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                val entries =
                    buildList {
                        add(pin to { actions.onSetPinned(note.id, !note.pinned) })
                        add(archive to { actions.onArchive(note.id) })
                        add(trash to { confirm = Confirm.Trash })
                        if (failed) {
                            add(retry to { actions.onRetryFailed(note.id) })
                            add(discard to { confirm = Confirm.Discard })
                        }
                    }
                for ((label, action) in entries) {
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            menuOpen = false
                            action()
                        },
                    )
                }
            }
        }
    }

    confirm?.let { which ->
        ConfirmDialog(
            title =
                stringResource(
                    if (which ==
                        Confirm.Trash
                    ) {
                        R.string.notes_trash_confirm_title
                    } else {
                        R.string.notes_discard_confirm_title
                    },
                ),
            message =
                stringResource(
                    if (which ==
                        Confirm.Trash
                    ) {
                        R.string.notes_trash_confirm_message
                    } else {
                        R.string.notes_discard_confirm_message
                    },
                ),
            confirmLabel = if (which == Confirm.Trash) trash else discard,
            onConfirm = {
                confirm = null
                if (which == Confirm.Trash) actions.onMoveToTrash(note.id) else actions.onDiscardFailed(note.id)
            },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.notes_confirm_cancel)) } },
    )
}

@Composable
private fun NoteCard(
    note: Note,
    dates: NoteDates,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (note.pinned) {
                    Text(
                        stringResource(R.string.notes_pinned),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            note.content?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (note.tags.isNotEmpty()) {
                Text(
                    text = note.tags.joinToString("  ") { "#$it" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val footer = footerOf(note, dates)
            if (footer.isNotEmpty()) {
                Text(
                    footer,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun footerOf(
    note: Note,
    dates: NoteDates,
): String {
    val edited = stringResource(R.string.notes_edited, dates.format(note.updatedAt).orEmpty())
    val date = if (note.isEdited) edited else dates.format(note.createdAt).orEmpty()
    val sync =
        when (note.syncState) {
            SyncState.Synced -> null
            SyncState.Pending -> stringResource(R.string.notes_sync_pending)
            SyncState.Failed -> stringResource(R.string.notes_sync_failed)
        }
    return listOfNotNull(date.takeIf { it.isNotEmpty() }, sync).joinToString(" · ")
}
