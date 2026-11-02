package com.mowtiie.writa.feature.trash

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mowtiie.writa.R
import com.mowtiie.writa.feature.notes.ui.ConfirmDialog
import com.mowtiie.writa.feature.notes.ui.Confirmation
import com.mowtiie.writa.feature.notes.ui.NoteAction
import com.mowtiie.writa.feature.notes.ui.NoteCollection

/** What the user can do in the trash. */
class TrashActions(
    val onRestore: (Long) -> Unit = {},
    val onDeleteForever: (Long) -> Unit = {},
    val onEmptyTrash: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@Composable
fun TrashDestination(
    onBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions =
        remember(viewModel) {
            TrashActions(
                viewModel::onRestore,
                viewModel::onDeleteForever,
                viewModel::onEmptyTrash,
                viewModel::onMessageShown,
            )
        }
    TrashScreen(state, actions, onBack)
}

@Composable
fun TrashScreen(
    state: TrashUiState,
    actions: TrashActions,
    onBack: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmEmpty by rememberSaveable { mutableStateOf(false) }

    val restore = stringResource(R.string.trash_restore)
    val deleteForever = stringResource(R.string.trash_delete_forever)
    val deleteForeverConfirmation =
        Confirmation(
            title = stringResource(R.string.trash_delete_forever_title),
            message = stringResource(R.string.trash_delete_forever_message),
        )
    NoteCollection(
        title = stringResource(R.string.trash_title),
        emptyText = stringResource(R.string.trash_empty),
        notes = state.notes,
        isLoaded = state.isLoaded,
        actionFailed = state.actionFailed,
        onMessageShown = actions.onMessageShown,
        onBack = onBack,
        actionsFor = { note ->
            listOf(
                NoteAction(restore) { actions.onRestore(note.id) },
                NoteAction(deleteForever, deleteForeverConfirmation) { actions.onDeleteForever(note.id) },
            )
        },
        topBarActions = {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.notes_menu_more))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.trash_menu_empty)) },
                    enabled = state.notes.isNotEmpty(),
                    onClick = {
                        menuOpen = false
                        confirmEmpty = true
                    },
                )
            }
        },
    )

    if (confirmEmpty) {
        ConfirmDialog(
            title = stringResource(R.string.trash_empty_title),
            message = stringResource(R.string.trash_empty_message),
            confirmLabel = stringResource(R.string.trash_menu_empty),
            onConfirm = {
                confirmEmpty = false
                actions.onEmptyTrash()
            },
            onDismiss = { confirmEmpty = false },
        )
    }
}
