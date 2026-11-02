package com.mowtiie.writa.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mowtiie.writa.core.datastore.AppSettings
import com.mowtiie.writa.core.datastore.ConnectionStore
import com.mowtiie.writa.core.datastore.SettingsStore
import com.mowtiie.writa.core.datastore.Theme
import com.mowtiie.writa.core.di.ApplicationScope
import com.mowtiie.writa.core.ui.theme.Contrast
import com.mowtiie.writa.feature.auth.AuthRepository
import com.mowtiie.writa.feature.notes.NoteRepository
import com.mowtiie.writa.feature.notes.attempt
import com.mowtiie.writa.feature.notes.remote.PartialImportException
import com.mowtiie.writa.feature.settings.backup.BackupNote
import com.mowtiie.writa.feature.settings.backup.BackupService
import com.mowtiie.writa.feature.settings.backup.DocumentOpener
import com.mowtiie.writa.feature.settings.backup.InvalidBackupException
import com.mowtiie.writa.feature.settings.backup.UnsupportedBackupVersionException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class Busy { Exporting, Importing }

sealed interface SettingsMessage {
    data object ExportDone : SettingsMessage

    data object ExportFailed : SettingsMessage

    data object NothingToExport : SettingsMessage

    data object ImportInvalid : SettingsMessage

    data object ImportUnsupportedVersion : SettingsMessage

    data object ImportFailed : SettingsMessage

    data class ImportDone(
        val count: Int,
    ) : SettingsMessage

    data class ImportPartial(
        val imported: Int,
        val total: Int,
    ) : SettingsMessage
}

sealed interface SettingsEvent {
    data class ChooseExportFile(
        val fileName: String,
    ) : SettingsEvent
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    /** Notes with changes the server does not have yet, which Change server would throw away. */
    val pendingChanges: Int = 0,
    val busy: Busy? = null,
    /** How many notes the chosen backup holds, while the user is asked whether to import them. */
    val importCount: Int? = null,
    val message: SettingsMessage? = null,
)

/**
 * The settings, and the backup and server actions that live with them. Appearance and privacy are written to the
 * settings store and the rest of the app follows. Import and export work on file locations the screen got from the
 * system file picker. Change server runs in the application scope: it ends with the app leaving this screen, which
 * must not cancel it half way.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsStore: SettingsStore,
        private val repository: NoteRepository,
        private val backup: BackupService,
        private val documents: DocumentOpener,
        private val auth: AuthRepository,
        private val connectionStore: ConnectionStore,
        @ApplicationScope private val applicationScope: CoroutineScope,
    ) : ViewModel() {
        private val local = MutableStateFlow(Local())
        private var chosenBackup: List<BackupNote> = emptyList()

        private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
        val events: Flow<SettingsEvent> = _events.receiveAsFlow()

        val state: StateFlow<SettingsUiState> =
            combine(settingsStore.settings, repository.observePendingCount(), local) { settings, pending, local ->
                SettingsUiState(settings, pending, local.busy, local.importCount, local.message)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

        fun onThemeChange(theme: Theme) = write { settingsStore.setTheme(theme) }

        fun onContrastChange(contrast: Contrast) = write { settingsStore.setContrast(contrast) }

        fun onDynamicColorChange(enabled: Boolean) = write { settingsStore.setDynamicColor(enabled) }

        fun onScreenPrivacyChange(enabled: Boolean) = write { settingsStore.setScreenPrivacy(enabled) }

        fun onExport() {
            viewModelScope.launch {
                if (backup.exportableCount() == 0) {
                    show(SettingsMessage.NothingToExport)
                } else {
                    _events.send(SettingsEvent.ChooseExportFile(backup.suggestedFileName()))
                }
            }
        }

        fun onExportFileChosen(uri: String) {
            viewModelScope.launch {
                local.update { it.copy(busy = Busy.Exporting) }
                val message =
                    if (attempt {
                            documents.write(uri) { out -> backup.export(out) }
                            true
                        }
                    ) {
                        SettingsMessage.ExportDone
                    } else {
                        SettingsMessage.ExportFailed
                    }
                local.update { it.copy(busy = null, message = message) }
            }
        }

        fun onImportFileChosen(uri: String) {
            viewModelScope.launch {
                local.update { it.copy(busy = Busy.Importing) }
                val notes = readBackup(uri)
                chosenBackup = notes.orEmpty()
                local.update { it.copy(busy = null, importCount = notes?.size) }
            }
        }

        fun onImportConfirm() {
            val notes = chosenBackup
            onImportDismiss()
            viewModelScope.launch {
                local.update { it.copy(busy = Busy.Importing) }
                val message = importMessage(notes)
                local.update { it.copy(busy = null, message = message) }
            }
        }

        fun onImportDismiss() {
            chosenBackup = emptyList()
            local.update { it.copy(importCount = null) }
        }

        fun onChangeServer() {
            applicationScope.launch {
                auth.signOut()
                repository.clear()
                connectionStore.clear()
            }
        }

        fun onMessageShown() = local.update { it.copy(message = null) }

        private fun write(change: suspend () -> Unit) {
            viewModelScope.launch { change() }
        }

        private fun show(message: SettingsMessage) = local.update { it.copy(message = message) }

        /** The notes of the chosen file, or null after telling the user why it cannot be used. */
        private suspend fun readBackup(uri: String): List<BackupNote>? {
            val notes =
                try {
                    documents.read(uri) { backup.read(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: InvalidBackupException) {
                    return failedToRead(SettingsMessage.ImportInvalid)
                } catch (_: UnsupportedBackupVersionException) {
                    return failedToRead(SettingsMessage.ImportUnsupportedVersion)
                } catch (_: Exception) {
                    return failedToRead(SettingsMessage.ImportFailed)
                }
            return notes.ifEmpty { failedToRead(SettingsMessage.ImportInvalid) }
        }

        private fun failedToRead(message: SettingsMessage): List<BackupNote>? {
            show(message)
            return null
        }

        private suspend fun importMessage(notes: List<BackupNote>): SettingsMessage =
            try {
                SettingsMessage.ImportDone(backup.import(notes))
            } catch (e: CancellationException) {
                throw e
            } catch (e: PartialImportException) {
                SettingsMessage.ImportPartial(e.imported, e.total)
            } catch (_: Exception) {
                SettingsMessage.ImportFailed
            }

        private data class Local(
            val busy: Busy? = null,
            val importCount: Int? = null,
            val message: SettingsMessage? = null,
        )

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
