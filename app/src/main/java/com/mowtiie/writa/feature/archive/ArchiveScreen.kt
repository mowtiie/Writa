package com.mowtiie.writa.feature.archive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mowtiie.writa.R
import com.mowtiie.writa.feature.notes.ui.Confirmation
import com.mowtiie.writa.feature.notes.ui.NoteAction
import com.mowtiie.writa.feature.notes.ui.NoteCollection

/** What the user can do on the archive. */
class ArchiveActions(
    val onUnarchive: (Long) -> Unit = {},
    val onMoveToTrash: (Long) -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@Composable
fun ArchiveDestination(
    onOpenNote: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: ArchiveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions =
        remember(viewModel) {
            ArchiveActions(viewModel::onUnarchive, viewModel::onMoveToTrash, viewModel::onMessageShown)
        }
    ArchiveScreen(state, actions, onOpenNote, onBack)
}

@Composable
fun ArchiveScreen(
    state: ArchiveUiState,
    actions: ArchiveActions,
    onOpenNote: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val unarchive = stringResource(R.string.archive_unarchive)
    val trash = stringResource(R.string.notes_action_trash)
    val trashConfirmation =
        Confirmation(
            title = stringResource(R.string.notes_trash_confirm_title),
            message = stringResource(R.string.notes_trash_confirm_message),
        )
    NoteCollection(
        title = stringResource(R.string.archive_title),
        emptyText = stringResource(R.string.archive_empty),
        notes = state.notes,
        isLoaded = state.isLoaded,
        actionFailed = state.actionFailed,
        onMessageShown = actions.onMessageShown,
        onBack = onBack,
        onOpen = { onOpenNote(it.id) },
        actionsFor = { note ->
            listOf(
                NoteAction(unarchive) { actions.onUnarchive(note.id) },
                NoteAction(trash, trashConfirmation) { actions.onMoveToTrash(note.id) },
            )
        },
    )
}
