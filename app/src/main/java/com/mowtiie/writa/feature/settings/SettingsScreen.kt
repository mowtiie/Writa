package com.mowtiie.writa.feature.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mowtiie.writa.R
import com.mowtiie.writa.core.datastore.Theme
import com.mowtiie.writa.core.ui.CollectEvents
import com.mowtiie.writa.core.ui.DialogContent
import com.mowtiie.writa.core.ui.PreferenceCategory
import com.mowtiie.writa.core.ui.PreferenceMinHeight
import com.mowtiie.writa.core.ui.PreferenceRow
import com.mowtiie.writa.core.ui.SwitchPreference
import com.mowtiie.writa.core.ui.theme.Contrast

/** What the user can do on the settings screen. */
class SettingsActions(
    val onThemeChange: (Theme) -> Unit = {},
    val onContrastChange: (Contrast) -> Unit = {},
    val onDynamicColorChange: (Boolean) -> Unit = {},
    val onScreenPrivacyChange: (Boolean) -> Unit = {},
    val onExport: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onImportConfirm: () -> Unit = {},
    val onImportDismiss: () -> Unit = {},
    val onChangeServer: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@Composable
fun SettingsDestination(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exportFile =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)) { uri ->
            uri?.let { viewModel.onExportFileChosen(it.toString()) }
        }
    val importFile =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { viewModel.onImportFileChosen(it.toString()) }
        }
    CollectEvents(viewModel.events) { event ->
        when (event) {
            is SettingsEvent.ChooseExportFile -> exportFile.launch(event.fileName)
        }
    }
    val actions =
        remember(viewModel, importFile) {
            SettingsActions(
                onThemeChange = viewModel::onThemeChange,
                onContrastChange = viewModel::onContrastChange,
                onDynamicColorChange = viewModel::onDynamicColorChange,
                onScreenPrivacyChange = viewModel::onScreenPrivacyChange,
                onExport = viewModel::onExport,
                onImport = { importFile.launch(arrayOf(BACKUP_MIME_TYPE)) },
                onImportConfirm = viewModel::onImportConfirm,
                onImportDismiss = viewModel::onImportDismiss,
                onChangeServer = viewModel::onChangeServer,
                onMessageShown = viewModel::onMessageShown,
            )
        }
    SettingsScreen(
        state = state,
        actions = actions,
        dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    dynamicColorAvailable: Boolean,
    onBack: () -> Unit,
) {
    val snackbarHost = remember { SnackbarHostState() }
    val message = state.message?.let { messageText(it) }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHost.showSnackbar(message)
            actions.onMessageShown()
        }
    }

    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            PreferenceCategory(R.string.settings_appearance)
            PreferenceRow(
                title = stringResource(R.string.settings_theme),
                summary = stringResource(themeLabel(state.settings.theme)),
                onClick = { dialog = SettingsDialog.Theme },
            )
            PreferenceRow(
                title = stringResource(R.string.settings_contrast),
                summary = stringResource(contrastLabel(state.settings.contrast)),
                onClick = { dialog = SettingsDialog.Contrast },
            )
            if (dynamicColorAvailable) {
                SwitchPreference(
                    title = stringResource(R.string.settings_dynamic_color),
                    summary = stringResource(R.string.settings_dynamic_color_summary),
                    checked = state.settings.dynamicColor,
                    onCheckedChange = actions.onDynamicColorChange,
                )
            }

            PreferenceCategory(R.string.settings_security)
            SwitchPreference(
                title = stringResource(R.string.settings_screen_privacy),
                summary = stringResource(R.string.settings_screen_privacy_summary),
                checked = state.settings.screenPrivacy,
                onCheckedChange = actions.onScreenPrivacyChange,
            )

            PreferenceCategory(R.string.settings_backup)
            PreferenceRow(
                title = stringResource(R.string.settings_import),
                summary = stringResource(R.string.settings_import_summary),
                onClick = actions.onImport,
            )
            PreferenceRow(
                title = stringResource(R.string.settings_export),
                summary = stringResource(R.string.settings_export_summary),
                onClick = actions.onExport,
            )

            PreferenceCategory(R.string.settings_configuration)
            PreferenceRow(
                title = stringResource(R.string.settings_change_server),
                summary = stringResource(R.string.settings_change_server_summary),
                onClick = { dialog = SettingsDialog.ChangeServer },
            )
        }
    }

    when (dialog) {
        SettingsDialog.Theme -> {
            ChoiceDialog(
                title = stringResource(R.string.settings_theme),
                options = Theme.entries.map { it to stringResource(themeLabel(it)) },
                selected = state.settings.theme,
                onSelect = actions.onThemeChange,
                onDismiss = { dialog = null },
            )
        }

        SettingsDialog.Contrast -> {
            ChoiceDialog(
                title = stringResource(R.string.settings_contrast),
                options = Contrast.entries.map { it to stringResource(contrastLabel(it)) },
                selected = state.settings.contrast,
                onSelect = actions.onContrastChange,
                onDismiss = { dialog = null },
            )
        }

        SettingsDialog.ChangeServer -> {
            ChangeServerDialog(
                unsentChanges = state.pendingChanges,
                onConfirm = {
                    dialog = null
                    actions.onChangeServer()
                },
                onDismiss = { dialog = null },
            )
        }

        null -> {
            Unit
        }
    }
    state.importCount?.let { count ->
        ImportDialog(count, onConfirm = actions.onImportConfirm, onDismiss = actions.onImportDismiss)
    }
    state.busy?.let { ProgressDialog(it) }
}

private enum class SettingsDialog { Theme, Contrast, ChangeServer }

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            DialogContent(Modifier.selectableGroup()) {
                for ((value, label) in options) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = value == selected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onSelect(value)
                                        onDismiss()
                                    },
                                ).heightIn(min = PreferenceMinHeight),
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(label, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.notes_confirm_cancel)) } },
    )
}

@Composable
private fun ChangeServerDialog(
    unsentChanges: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val message =
        buildString {
            append(stringResource(R.string.settings_change_server_message))
            if (unsentChanges > 0) {
                append("\n\n")
                append(pluralStringResource(R.plurals.notes_sign_out_unsent, unsentChanges, unsentChanges))
            }
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_change_server_title)) },
        text = { DialogContent { Text(message) } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.settings_continue)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.notes_confirm_cancel)) } },
    )
}

@Composable
private fun ImportDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_import_title)) },
        text = { DialogContent { Text(pluralStringResource(R.plurals.settings_import_message, count, count)) } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.settings_import)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.notes_confirm_cancel)) } },
    )
}

@Composable
private fun ProgressDialog(busy: Busy) {
    val message =
        stringResource(if (busy == Busy.Exporting) R.string.settings_exporting else R.string.settings_importing)
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Text(message, modifier = Modifier.padding(start = 24.dp))
            }
        },
    )
}

private fun themeLabel(theme: Theme): Int =
    when (theme) {
        Theme.System -> R.string.settings_theme_system
        Theme.Battery -> R.string.settings_theme_battery
        Theme.Light -> R.string.settings_theme_light
        Theme.Dark -> R.string.settings_theme_dark
    }

private fun contrastLabel(contrast: Contrast): Int =
    when (contrast) {
        Contrast.Low -> R.string.settings_contrast_low
        Contrast.Medium -> R.string.settings_contrast_medium
        Contrast.High -> R.string.settings_contrast_high
    }

@Composable
private fun messageText(message: SettingsMessage): String =
    when (message) {
        SettingsMessage.ExportDone -> {
            stringResource(R.string.settings_export_done)
        }

        SettingsMessage.ExportFailed -> {
            stringResource(R.string.settings_export_failed)
        }

        SettingsMessage.NothingToExport -> {
            stringResource(R.string.settings_nothing_to_export)
        }

        SettingsMessage.ImportInvalid -> {
            stringResource(R.string.settings_import_invalid)
        }

        SettingsMessage.ImportUnsupportedVersion -> {
            stringResource(R.string.settings_import_unsupported)
        }

        SettingsMessage.ImportFailed -> {
            stringResource(R.string.settings_import_failed)
        }

        is SettingsMessage.ImportDone -> {
            pluralStringResource(R.plurals.settings_import_done, message.count, message.count)
        }

        is SettingsMessage.ImportPartial -> {
            stringResource(R.string.settings_import_partial, message.imported, message.total)
        }
    }

private const val BACKUP_MIME_TYPE = "application/json"
