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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.material3.TopAppBarScrollBehavior
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mowtiie.writa.R
import com.mowtiie.writa.core.ui.DialogContent
import com.mowtiie.writa.core.ui.ScrollToTopWhenFirstItemChanges
import com.mowtiie.writa.core.ui.WithoutPersonalizedLearning
import com.mowtiie.writa.feature.notes.Note
import com.mowtiie.writa.feature.notes.RefreshFailure
import com.mowtiie.writa.feature.notes.Sort
import com.mowtiie.writa.feature.notes.SyncState
import com.mowtiie.writa.feature.notes.ui.ConfirmDialog
import com.mowtiie.writa.feature.notes.ui.NoteCard

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
    val onSignOut: () -> Unit = {},
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
                onSignOut = viewModel::signOut,
                onMessageShown = viewModel::onMessageShown,
            )
        }
    if (state.schemaOutdated) {
        SchemaOutdatedDestination(onCheckAgain = viewModel::refresh)
    } else {
        NotesScreen(state, actions, navigation)
    }
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
    var showSignOut by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                if (state.isSearchOpen) {
                    SearchTopBar(state.search, actions.onSearchChange, actions.onSearchClose, scrollBehavior)
                } else {
                    NotesTopBar(
                        onSearch = actions.onSearchOpen,
                        onSort = { showSort = true },
                        onSignOut = { showSignOut = true },
                        navigation = navigation,
                        scrollBehavior = scrollBehavior,
                    )
                }
                if (state.tags.isNotEmpty()) TagBar(state.tags, actions.onTagToggle)
                state.banner?.let { SavedNotesBanner(it, actions.onRefresh) }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = navigation.onCreateNote) {
                Icon(painterResource(R.drawable.ic_plus), contentDescription = stringResource(R.string.notes_add_note))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            NoteList(state, actions, navigation)
        }
    }

    if (showSort) {
        SortDialog(current = state.sort, onSelected = { actions.onSortSelected(it) }, onDismiss = { showSort = false })
    }
    if (showSignOut) {
        SignOutDialog(
            unsentChanges = state.pendingChanges,
            onConfirm = {
                showSignOut = false
                actions.onSignOut()
            },
            onDismiss = { showSignOut = false },
        )
    }
}

// ---------------------------- the bars ----------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesTopBar(
    onSearch: () -> Unit,
    onSort: () -> Unit,
    onSignOut: () -> Unit,
    navigation: NotesNavigation,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(stringResource(R.string.app_name), modifier = Modifier.semantics { heading() }) },
        scrollBehavior = scrollBehavior,
        actions = {
            IconButton(onClick = onSearch) {
                Icon(
                    painterResource(R.drawable.ic_search),
                    contentDescription = stringResource(R.string.notes_menu_search),
                )
            }
            IconButton(onClick = onSort) {
                Icon(painterResource(R.drawable.ic_sort), contentDescription = stringResource(R.string.notes_menu_sort))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.notes_menu_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    val entries =
                        listOf(
                            R.string.notes_menu_archive to navigation.onOpenArchive,
                            R.string.notes_menu_trash to navigation.onOpenTrash,
                            R.string.notes_menu_settings to navigation.onOpenSettings,
                            R.string.notes_menu_about to navigation.onOpenAbout,
                            R.string.notes_menu_sign_out to onSignOut,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    search: String,
    onChange: (String) -> Unit,
    onClose: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // The cursor belongs at the end of what was typed, also when the text comes back after a rotation.
    var field by remember { mutableStateOf(TextFieldValue(search, TextRange(search.length))) }
    if (field.text != search) field = TextFieldValue(search, TextRange(search.length))
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.notes_search_close),
                )
            }
        },
        title = {
            WithoutPersonalizedLearning {
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
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        },
        actions = {
            if (search.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.notes_search_clear))
                }
            }
        },
        scrollBehavior = scrollBehavior,
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
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .combinedClickable(onClick = onRetry)
                .heightIn(min = 48.dp)
                .wrapContentHeight(Alignment.CenterVertically)
                .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private fun bannerText(reason: RefreshFailure): Int =
    when (reason) {
        RefreshFailure.Offline -> R.string.notes_banner_offline
        RefreshFailure.Server -> R.string.notes_banner_server
        RefreshFailure.SecureConnection -> R.string.notes_banner_secure
        RefreshFailure.Unavailable -> R.string.notes_banner_unavailable
        RefreshFailure.SchemaOutdated, RefreshFailure.Other -> R.string.notes_banner_other
    }

@Composable
private fun TagBar(
    tags: List<TagCount>,
    onToggle: (String) -> Unit,
) {
    val description = stringResource(R.string.notes_filter_by_tag)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .semantics { contentDescription = description },
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
private fun SignOutDialog(
    unsentChanges: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val unsent =
        if (unsentChanges > 0) {
            "\n\n" + pluralStringResource(R.plurals.notes_sign_out_unsent, unsentChanges, unsentChanges)
        } else {
            ""
        }
    ConfirmDialog(
        title = stringResource(R.string.notes_sign_out_title),
        message = stringResource(R.string.notes_sign_out_message) + unsent,
        confirmLabel = stringResource(R.string.notes_menu_sign_out),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
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
            DialogContent {
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

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.items, key = { it.key }) { item ->
            SwipeableNote(item.note, dates, actions, navigation)
        }
    }
    when {
        !state.isLoaded -> LoadingState()
        state.emptyReason != null -> EmptyState(state.emptyReason, actions.onRefresh)
    }
}

@Composable
private fun LoadingState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.notes_loading),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun EmptyState(
    reason: EmptyReason,
    onRetry: () -> Unit,
) {
    val text =
        when (reason) {
            EmptyReason.NoNotes -> stringResource(R.string.notes_empty)
            is EmptyReason.NoMatchForSearch -> stringResource(R.string.notes_empty_search, reason.search)
            EmptyReason.NoMatchForTags -> stringResource(R.string.notes_empty_tags)
            EmptyReason.LoadFailed -> stringResource(R.string.notes_empty_offline)
        }
    val icon = if (reason == EmptyReason.LoadFailed) R.drawable.ic_offline else R.drawable.ic_notes
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(EMPTY_ICON_SIZE),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 36.dp, top = 8.dp, end = 36.dp),
        )
        if (reason == EmptyReason.LoadFailed) {
            FilledTonalButton(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.notes_retry))
            }
        }
    }
}

private val EMPTY_ICON_SIZE = 125.dp

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
