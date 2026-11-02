package com.mowtiie.writa.feature.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mowtiie.writa.R
import com.mowtiie.writa.core.ui.CollectEvents
import com.mowtiie.writa.core.ui.WithoutPersonalizedLearning
import com.mowtiie.writa.core.ui.submitOnHardwareEnter
import com.mowtiie.writa.feature.notes.Tags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** What the user can do in the editor. */
class EditorActions(
    val onTitleChange: (String) -> Unit = {},
    val onContentChange: (String) -> Unit = {},
    val onPinToggle: () -> Unit = {},
    val onTagInputChange: (String) -> Unit = {},
    val onTagCommit: () -> Unit = {},
    val onTagPicked: (String) -> Unit = {},
    val onTagRemove: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onArchiveToggle: () -> Unit = {},
    val onTrash: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onClose: () -> Unit = {},
)

@Composable
fun EditorDestination(
    onClose: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions =
        remember(viewModel, onClose) {
            EditorActions(
                onTitleChange = viewModel::onTitleChange,
                onContentChange = viewModel::onContentChange,
                onPinToggle = viewModel::onPinToggle,
                onTagInputChange = viewModel::onTagInputChange,
                onTagCommit = viewModel::onTagCommit,
                onTagPicked = viewModel::onTagPicked,
                onTagRemove = viewModel::onTagRemove,
                onSave = viewModel::onSave,
                onArchiveToggle = viewModel::onArchiveToggle,
                onTrash = viewModel::onTrash,
                onMessageShown = viewModel::onMessageShown,
                onClose = onClose,
            )
        }
    EditorScreen(state, actions, viewModel.events)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: EditorUiState,
    actions: EditorActions,
    events: Flow<EditorEvent> = emptyFlow(),
) {
    CollectEvents(events) { event ->
        when (event) {
            EditorEvent.Close -> actions.onClose()
        }
    }

    val snackbarHost = remember { SnackbarHostState() }
    val message = state.message?.let { messageText(it) }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHost.showSnackbar(message)
            actions.onMessageShown()
        }
    }

    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var showTrash by rememberSaveable { mutableStateOf(false) }
    val requestClose = { if (state.hasUnsavedChanges) showDiscard = true else actions.onClose() }
    BackHandler(onBack = requestClose)

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            EditorTopBar(
                state = state,
                actions = actions,
                onNavigateUp = requestClose,
                onTrash = { showTrash = true },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state.load) {
                EditorLoad.Loading -> LoadingNote()
                EditorLoad.Unavailable -> UnavailableNote()
                EditorLoad.Ready -> WithoutPersonalizedLearning { NoteForm(state, actions) }
            }
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = { Text(stringResource(R.string.editor_discard_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscard = false
                    actions.onClose()
                }) { Text(stringResource(R.string.editor_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) { Text(stringResource(R.string.editor_keep_editing)) }
            },
        )
    }
    if (showTrash) {
        AlertDialog(
            onDismissRequest = { showTrash = false },
            title = { Text(stringResource(R.string.notes_trash_confirm_title)) },
            text = { Text(stringResource(R.string.notes_trash_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showTrash = false
                    actions.onTrash()
                }) { Text(stringResource(R.string.editor_menu_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showTrash = false }) { Text(stringResource(R.string.notes_confirm_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    state: EditorUiState,
    actions: EditorActions,
    onNavigateUp: () -> Unit,
    onTrash: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val editable = state.load == EditorLoad.Ready
    TopAppBar(
        title = {
            val title = if (state.isNew) R.string.editor_title_new else R.string.editor_title_edit
            Text(stringResource(title), modifier = Modifier.semantics { heading() })
        },
        navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.navigate_up),
                )
            }
        },
        actions = {
            if (!editable) return@TopAppBar
            IconButton(onClick = actions.onSave, enabled = !state.isSaving) {
                Icon(
                    painterResource(R.drawable.ic_save),
                    contentDescription = stringResource(R.string.editor_menu_save),
                )
            }
            val pinned = state.draft.pinned
            IconButton(onClick = actions.onPinToggle) {
                Icon(
                    painter = painterResource(R.drawable.ic_pin),
                    contentDescription = stringResource(pinToggleLabel(pinned)),
                    tint = pinTint(pinned),
                )
            }
            if (!state.isNew) {
                IconButton(onClick = onTrash, enabled = !state.isSaving) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.editor_menu_delete),
                    )
                }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.notes_menu_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    archiveToggleLabel(state.archived),
                                ),
                            )
                        },
                        enabled = !state.isSaving,
                        onClick = {
                            menuOpen = false
                            actions.onArchiveToggle()
                        },
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun NoteForm(
    state: EditorUiState,
    actions: EditorActions,
) {
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(state.titleMissing) { if (state.titleMissing) titleFocus.requestFocus() }
    LaunchedEffect(Unit) { if (state.isNew && state.draft.title.isEmpty()) titleFocus.requestFocus() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        val titleError = if (state.titleMissing) stringResource(R.string.editor_error_empty_title) else null
        OutlinedTextField(
            value = state.draft.title,
            onValueChange = actions.onTitleChange,
            label = { Text(stringResource(R.string.editor_hint_title)) },
            isError = titleError != null,
            supportingText = titleError?.let { { Text(it) } },
            maxLines = TITLE_MAX_LINES,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocus)
                    .semantics { titleError?.let { error(it) } },
        )
        TagsSection(state, actions, Modifier.padding(top = 8.dp))
        OutlinedTextField(
            value = state.draft.content,
            onValueChange = actions.onContentChange,
            label = { Text(stringResource(R.string.editor_hint_content)) },
            minLines = CONTENT_MIN_LINES,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagsSection(
    state: EditorUiState,
    actions: EditorActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (tag in state.draft.tags) {
                val remove = stringResource(R.string.editor_remove_tag, tag)
                InputChip(
                    selected = false,
                    onClick = { actions.onTagRemove(tag) },
                    label = { Text(tag) },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                    modifier = Modifier.semantics { contentDescription = remove },
                )
            }
        }

        var expanded by remember { mutableStateOf(false) }
        val suggestions = state.suggestions
        val limitError =
            if (state.tagLimitReached) {
                pluralStringResource(R.plurals.editor_error_tags_limit, Tags.MAX_TAGS_PER_NOTE, Tags.MAX_TAGS_PER_NOTE)
            } else {
                null
            }
        ExposedDropdownMenuBox(
            expanded = expanded && suggestions.isNotEmpty(),
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = state.tagInput,
                onValueChange = {
                    expanded = true
                    actions.onTagInputChange(it)
                },
                label = { Text(stringResource(R.string.editor_hint_tag)) },
                isError = limitError != null,
                supportingText = limitError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { actions.onTagCommit() }),
                modifier =
                    Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                        .fillMaxWidth()
                        .submitOnHardwareEnter(actions.onTagCommit)
                        .semantics { limitError?.let { error(it) } },
            )
            ExposedDropdownMenu(
                expanded = expanded && suggestions.isNotEmpty(),
                onDismissRequest = { expanded = false },
            ) {
                for (suggestion in suggestions) {
                    DropdownMenuItem(
                        text = { Text(suggestion) },
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        onClick = {
                            expanded = false
                            actions.onTagPicked(suggestion)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingNote() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.editor_loading),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun UnavailableNote() {
    Box(Modifier.fillMaxSize().padding(36.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.editor_unavailable),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun messageText(message: EditorMessage): String =
    when (message) {
        EditorMessage.SaveFailed -> stringResource(R.string.editor_save_failed)
        EditorMessage.TrashFailed -> stringResource(R.string.editor_trash_failed)
    }

private const val TITLE_MAX_LINES = 2
private const val CONTENT_MIN_LINES = 10

private fun pinToggleLabel(pinned: Boolean) = if (pinned) R.string.editor_menu_unpin else R.string.editor_menu_pin

private fun archiveToggleLabel(archived: Boolean) =
    if (archived) R.string.editor_menu_unarchive else R.string.editor_menu_archive

@Composable
private fun pinTint(pinned: Boolean) =
    if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
