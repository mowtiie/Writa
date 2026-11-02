package com.mowtiie.writa.feature.notes.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mowtiie.writa.R
import com.mowtiie.writa.feature.notes.Note
import com.mowtiie.writa.feature.notes.list.NoteDates

/** Something the user can do to a note in a collection, optionally after being asked to confirm. */
internal class NoteAction(
    val label: String,
    val confirmation: Confirmation? = null,
    val run: () -> Unit,
)

internal class Confirmation(
    val title: String,
    val message: String,
)

/**
 * A screen that lists some of the notes, such as the archive or the trash. Tapping a note calls [onOpen], or shows
 * its actions when there is none; a long press always shows them, and screen readers get them as custom actions.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun NoteCollection(
    title: String,
    emptyText: String,
    notes: List<Note>,
    isLoaded: Boolean,
    actionFailed: Boolean,
    onMessageShown: () -> Unit,
    onBack: () -> Unit,
    actionsFor: @Composable (Note) -> List<NoteAction>,
    modifier: Modifier = Modifier,
    onOpen: ((Note) -> Unit)? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
) {
    val snackbarHost = remember { SnackbarHostState() }
    val failedText = stringResource(R.string.notes_action_failed)
    LaunchedEffect(actionFailed) {
        if (actionFailed) {
            snackbarHost.showSnackbar(failedText)
            onMessageShown()
        }
    }

    var menuFor by remember { mutableStateOf<Note?>(null) }
    var confirming by remember { mutableStateOf<NoteAction?>(null) }
    val request: (NoteAction) -> Unit = { action ->
        if (action.confirmation != null) confirming = action else action.run()
    }
    val dates = remember { NoteDates() }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(title, modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
                actions = topBarActions,
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(notes, key = { it.id }) { note ->
                    val actions = actionsFor(note)
                    NoteCard(
                        note = note,
                        dates = dates,
                        modifier =
                            Modifier
                                .combinedClickable(
                                    onClick = { if (onOpen != null) onOpen(note) else menuFor = note },
                                    onLongClick = { menuFor = note },
                                ).semantics {
                                    customActions =
                                        actions.map { action ->
                                            CustomAccessibilityAction(action.label) {
                                                request(action)
                                                true
                                            }
                                        }
                                },
                    )
                }
            }
            if (isLoaded && notes.isEmpty()) EmptyLabel(emptyText)
        }
    }

    menuFor?.let { note ->
        ActionsDialog(
            title = note.title,
            actions = actionsFor(note),
            onSelect = {
                menuFor = null
                request(it)
            },
            onDismiss = { menuFor = null },
        )
    }
    confirming?.let { action ->
        action.confirmation?.let { confirmation ->
            ConfirmDialog(
                title = confirmation.title,
                message = confirmation.message,
                confirmLabel = action.label,
                onConfirm = {
                    confirming = null
                    action.run()
                },
                onDismiss = { confirming = null },
            )
        }
    }
}

@Composable
private fun EmptyLabel(text: String) {
    Box(Modifier.fillMaxSize().padding(36.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ActionsDialog(
    title: String,
    actions: List<NoteAction>,
    onSelect: (NoteAction) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2) },
        text = {
            Column {
                for (action in actions) {
                    TextButton(onClick = { onSelect(action) }, modifier = Modifier.fillMaxWidth()) {
                        Text(action.label, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.notes_confirm_cancel)) } },
    )
}
